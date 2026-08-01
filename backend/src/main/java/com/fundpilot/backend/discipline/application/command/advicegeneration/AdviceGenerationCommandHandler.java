package com.fundpilot.backend.discipline.application.command.advicegeneration;

import com.fundpilot.backend.discipline.application.gateway.advicegeneration.GeneratedAdvicePortfolioGateway;
import com.fundpilot.backend.discipline.application.gateway.advicegeneration.AdviceGenerationFactsGateway;
import com.fundpilot.backend.discipline.domain.advice.AdvicePolicy;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.advice.AdviceResponseStatus;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.platform.transaction.RequiresNewTransactionExecutor;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.time.Instant;
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
    private final RequiresNewTransactionExecutor transactions;

    public void generateDaily(Instant occurredAt) {
        Instant businessDate = ChinaTradingDate.toUtcDate(occurredAt);
        if (!facts.isTradingDay(businessDate)) {
            log.info("非交易日跳过纪律建议生成 date={}", businessDate);
            return;
        }
        for (DisciplineStrategy strategy : strategies.findEffective()) {
            try {
                transactions.execute(() -> {
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
        if (strategy == null || !"EFFECTIVE".equals(strategy.status())) {
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

        if ("LOGIC_BROKEN".equals(result.reason()) && strategy.triggeredAdviceId() != null) {
            advice.findByIdForUpdate(strategy.triggeredAdviceId())
                    .filter(current -> current.responseStatus() == AdviceResponseStatus.PENDING)
                    .filter(current -> !businessDate.equals(current.signalDate()))
                    .ifPresent(current -> {
                        current.ignore(businessDate);
                        advice.save(current);
                    });
            strategy.supersedeTriggered();
        } else if ("TRIGGERED".equals(strategy.takeProfitPhase()) && result.action() == AdviceAction.NONE) {
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
}
