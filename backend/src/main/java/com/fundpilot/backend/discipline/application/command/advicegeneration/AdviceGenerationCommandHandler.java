package com.fundpilot.backend.discipline.application.command.advicegeneration;

import com.fundpilot.backend.discipline.application.gateway.advicegeneration.GeneratedAdvicePortfolioGateway;
import com.fundpilot.backend.discipline.application.gateway.advicegeneration.AdviceGenerationFactsGateway;
import com.fundpilot.backend.discipline.application.gateway.adviceresponse.AdviceTransactionGateway;
import com.fundpilot.backend.discipline.domain.advice.Advice;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdvicePolicy;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.advice.AdviceResponseStatus;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.discipline.domain.strategy.StrategyParamStatus;
import com.fundpilot.backend.discipline.domain.strategy.TakeProfitPhase;
import com.fundpilot.backend.platform.transaction.RequiresNewTransactionExecutor;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将策略评估结果写入 Discipline 建议日志；同日未回应建议允许重算覆盖。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdviceGenerationCommandHandler {
    private final AdviceRepository advice;
    private final DisciplineStrategyRepository strategies;
    private final AdviceGenerationFactsGateway facts;
    private final AdviceTransactionGateway transactions;
    private final RequiresNewTransactionExecutor transactionExecutor;

    public void generateDaily(Instant occurredAt) {
        Instant businessDate = ChinaTradingDate.toUtcDate(occurredAt);
        if (!facts.isTradingDay(businessDate)) {
            log.info("非交易日跳过纪律建议生成 date={}", businessDate);
            return;
        }
        for (DisciplineStrategy strategy : strategies.findEffective()) {
            try {
                transactionExecutor.execute(() -> {
                    generate(strategy.id(), businessDate);
                    return null;
                });
            } catch (RuntimeException exception) {
                log.error("纪律建议生成失败 portfolio_fund={} date={}: {}", strategy.portfolioFundId(),
                        businessDate, exception.getMessage(), exception);
            }
        }
    }

    @Transactional
    public void generate(long strategyId, Instant businessDate) {
        DisciplineStrategy strategy = strategies.findById(strategyId).orElse(null);
        if (strategy == null || strategy.status() != StrategyParamStatus.EFFECTIVE) {
            return;
        }
        var loaded = facts.load(strategy.ownerId(), strategy.portfolioFundId(), businessDate);
        if (loaded.isEmpty()) {
            return;
        }
        var value = loaded.get();
        long daysSinceLastBuy = value.lastBuyTime() == null ? 6
                : facts.tradingDaysBetween(ChinaTradingDate.toUtcDate(value.lastBuyTime()), businessDate);
        boolean cooldownFinished = strategy.cooldownStartedAt() == null
                || facts.tradingDaysBetween(ChinaTradingDate.toUtcDate(strategy.cooldownStartedAt()), businessDate)
                >= strategy.cooldownDays();
        AdviceGenerationFactsGateway.MarketSnapshot market = value.market();
        AdvicePolicy.Result result = new AdvicePolicy().evaluate(strategy, new AdvicePolicy.Facts(
                value.productType(), value.positionStatus(), value.costPerShare(), value.holdingShares(),
                market == null ? null : new AdvicePolicy.Market(market.priceAboveYearLine(),
                        market.weeklyMacdState(), market.volumeState()),
                value.currentUnitNav(), value.currentAccumulatedNav(), value.matureRedeemableShares(), businessDate),
                daysSinceLastBuy, cooldownFinished);
        if (result.action() == AdviceAction.SELL) {
            // 已有在途卖出建议时抑制或替换，保证"同一基金最多一笔在途卖出"的一次性语义
            if (!supersedeOrBlock(strategy, result, businessDate)) {
                return;
            }
        } else if (strategy.takeProfitPhase() == TakeProfitPhase.TRIGGERED) {
            strategies.save(strategy);
            return;
        }

        var saved = advice.replaceGenerated(strategy.portfolioFundId(), strategy.ownerId(), strategy.id(),
                businessDate, result.action(), null, null, result.suggestedValue(), result.suggestedMeasureUnit(),
                result.reason(), result.warnings().isEmpty() ? null : String.join(",", result.warnings()), null);
        if ("TRAILING_STOP".equals(result.reason()) && saved.responseStatus() == AdviceResponseStatus.PENDING) {
            strategy.markTriggered(saved.id());
        }
        strategies.save(strategy);
    }

    /**
     * 在途卖出抑制(issue #183)：同基金已有未决(PENDING 未回应)或已采纳未确认(RESPONDED 且交易未确认)的
     * 卖出建议时，不再重复生成新卖出建议，避免多条 PENDING 卖出堆积与双卖。
     * <p>例外一：新命中为 LOGIC_BROKEN 且旧建议仍 PENDING 时，按"逻辑止损优先级"忽略旧建议后继续生成
     * (与 TRAILING_STOP 的 markTriggered 抑制对称)；同日重算不忽略(由 replaceGenerated 覆盖)。
     * <p>例外二：同日重算覆盖——已有建议就是今天生成的，允许重算覆盖(对已 RESPONDED 行 replaceGenerated 本身会跳过)。
     *
     * @return true 表示可继续生成新建议，false 表示已抑制(调用方直接返回)
     */
    private boolean supersedeOrBlock(DisciplineStrategy strategy, AdvicePolicy.Result result, Instant businessDate) {
        Optional<Advice> current = advice.findLatestSellAdviceByPortfolioFund(strategy.portfolioFundId());
        if (current.isEmpty()) {
            return true;
        }
        Advice value = current.get();
        boolean sameDay = businessDate.equals(value.signalDate());
        boolean unresponded = value.responseStatus() == AdviceResponseStatus.PENDING;
        boolean acceptedInFlight = value.responseStatus() == AdviceResponseStatus.RESPONDED
                && transactions.relatedTransaction(value.id())
                .map(related -> related.status() != AdviceTransactionGateway.Status.CONFIRMED).orElse(false);

        if (unresponded && "LOGIC_BROKEN".equals(result.reason())) {
            if (!sameDay) {
                value.ignore(businessDate);
                advice.save(value);
            }
            strategy.supersedeTriggered();
            return true;
        }
        if ((unresponded || acceptedInFlight) && !sameDay) {
            strategies.save(strategy);
            return false;
        }
        return true;
    }
}
