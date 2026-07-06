package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalWarningValue;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.DisciplineStrategyService;
import com.fundpilot.backend.strategy.service.support.CapitalContext;
import com.fundpilot.backend.strategy.service.support.MarketIndicators;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 信号生成编排服务(issue #13):每日 14:50 遍历所有绑定 EFFECTIVE 策略的基金,
 * 调 {@link DisciplineStrategyService#evaluateSignal} 并落 {@link SignalLogEntity}。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>查所有 EFFECTIVE 策略对应的 fund_id 列表</li>
 *   <li>每只基金:读 market_indicator_snapshot(缺→NONE+INSUFFICIENT_MARKET_DATA);算 CapitalContext;调 evaluateSignal;
 *       覆盖式落 SignalLog(软删同日旧行+写新);写回 fund_strategy 的 tierNAddedAt(反弹清空副作用)</li>
 *   <li>单只基金异常 try/catch 记 ERROR 日志,不影响其他基金</li>
 * </ol>
 *
 * <h3>重跑覆盖</h3>
 * 唯一约束 {@code uq_signal_log_daily (fund_id, signal_date::date)}——同日重跑时软删旧 + 写新,支持手动重跑调试。
 */
@Service
@RequiredArgsConstructor
public class SignalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 7 天内不赎回硬约束窗口(交易日),与 DisciplineStrategyService.MIN_HOLD_DAYS 一致。 */
    private static final int MIN_HOLD_DAYS = 5;

    private final FundStrategyRepository fundStrategyRepository;
    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final FundPositionService fundPositionService;
    private final MarketIndicatorProvider marketIndicatorProvider;
    private final SignalLogRepository signalLogRepository;
    private final TradingCalendarService tradingCalendarService;
    private final DisciplineStrategyService disciplineStrategyService;

    /**
     * 生成指定日期的全量信号。每只 EFFECTIVE 基金落一行 SignalLog(含 NONE 兜底)。
     * 单只基金异常不影响其他基金。
     */
    @Transactional
    public void generateDailySignals(Instant date) {
        List<Long> fundIds = fundStrategyRepository.findEffectiveFundIds();
        for (Long fundId : fundIds) {
            try {
                generateForFund(fundId, date);
            } catch (RuntimeException ex) {
                log.error("信号生成失败 fund_id={} date={}: {}", fundId, date, ex.getMessage(), ex);
            }
        }
    }

    private void generateForFund(Long fundId, Instant date) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) {
            return;
        }
        Optional<FundStrategyEntity> strategyOpt =
                fundStrategyRepository.findByFundEntity_IdAndStatus(fundId, StrategyParamStatus.EFFECTIVE);
        if (strategyOpt.isEmpty()) {
            return;
        }
        FundStrategyEntity strategy = strategyOpt.get();

        Instant dayStart = date;
        Instant dayEnd = date.plus(1, java.time.temporal.ChronoUnit.DAYS);

        Optional<MarketIndicatorSnapshotEntity> snapshotOpt = marketIndicatorProvider.getIndicators(fundId, date);
        SignalResult result;
        if (snapshotOpt.isEmpty()) {
            result = SignalResult.none(SignalReason.INSUFFICIENT_MARKET_DATA);
        } else {
            MarketIndicators market = toMarketIndicators(snapshotOpt.get());
            CapitalContext capital = buildCapitalContext(fund, strategy, market, date);
            long tradingDaysSinceLastBuy = computeTradingDaysSinceLastBuy(fund, strategy, date);
            result = disciplineStrategyService.evaluateSignal(fund, strategy, market, capital, dayStart, tradingDaysSinceLastBuy);
        }

        // 反弹清空副作用:tierNAddedAt 变更随信号生成一起写回 fund_strategy
        fundStrategyRepository.save(strategy);

        // 覆盖式落 SignalLog:软删同日旧行 + 写新
        signalLogRepository.findByFundEntity_IdAndSignalDateBetween(fundId, dayStart, dayEnd)
                .forEach(signalLogRepository::delete);
        SignalLogEntity log = toSignalLogEntity(fund, strategy, result, dayStart);
        signalLogRepository.save(log);
    }

    private MarketIndicators toMarketIndicators(MarketIndicatorSnapshotEntity snapshot) {
        return new MarketIndicators(
                snapshot.getCurrentNav(),
                snapshot.isPriceAboveYearLine(),
                snapshot.isYearLineRising(),
                snapshot.getWeeklyMacdState(),
                snapshot.getVolumeState(),
                snapshot.getWeeklyDropPercent(),
                snapshot.isSixtyDayHigh(),
                snapshot.getVolumeState(), // 跟踪指数量能本期复用基金量能(ETF 单独拉指数量能留待后续)
                false); // benchmarkDroppedToday 本期默认 false(需跟踪指数日K,后续补)
    }

    private CapitalContext buildCapitalContext(FundEntity fund, FundStrategyEntity strategy,
                                                MarketIndicators market, Instant date) {
        BigDecimal currentNav = market.currentNav() != null ? market.currentNav() : BigDecimal.ZERO;
        BigDecimal peakNav = fundNavHistoryRepository.findPeakAccumulatedNav(fund.getId()).orElse(currentNav);
        BigDecimal holdingPeakNav = fund.getOpenedAt() != null
                ? fundNavHistoryRepository.findPeakAccumulatedNavSince(fund.getId(), fund.getOpenedAt()).orElse(currentNav)
                : peakNav;
        BigDecimal holdingShares = fundPositionService.getHoldingShares(fund.getId());
        Instant lastBuyConfirmTime = computeLastBuyConfirmTime(fund);

        return new CapitalContext(peakNav, holdingPeakNav, holdingShares, lastBuyConfirmTime);
    }

    /** 最近一次买入确认时间 = 最近一笔 CONFIRMED 交易的 confirmTime(金字塔 tierAddedAt 已移除)。 */
    private Instant computeLastBuyConfirmTime(FundEntity fund) {
        FundTransactionEntity latest = fundTransactionRepository.findTopByFundEntity_IdOrderByConfirmTimeDesc(fund.getId());
        if (latest == null) {
            throw new IllegalArgumentException("Fund " + fund.getId() + " has no confirm transaction.");
        }
        return latest.getConfirmTime();
    }

    private long computeTradingDaysSinceLastBuy(FundEntity fund, FundStrategyEntity strategy, Instant today) {
        Instant lastBuy = computeLastBuyConfirmTime(fund);
        if (lastBuy == null) {
            return MIN_HOLD_DAYS + 1; // 无买入记录视为已满窗口
        }
        return tradingCalendarService.daysBetweenTradingDays(lastBuy, today);
    }

    private static SignalLogEntity toSignalLogEntity(FundEntity fund, FundStrategyEntity strategy,
                                                      SignalResult result, Instant signalDate) {
        SignalLogEntity entity = new SignalLogEntity();
        entity.setFundEntity(fund);
        entity.setFundStrategyEntity(strategy);
        entity.setSignalDate(signalDate);
        entity.setTriggerNav(null); // triggerNav 本期不单独存(可从 snapshot 补)
        entity.setTriggerTier(result.triggerTier());
        entity.setCoefficient(result.coefficient());
        entity.setSignalType(result.signalType());
        entity.setSuggestedMeasure(result.suggestedMeasure());
        entity.setReason(result.reason());
        entity.setWarnings(result.warnings().isEmpty() ? null
                : result.warnings().stream().map(SignalWarningValue::toPersistedString)
                        .reduce((a, b) -> a + "," + b).orElse(null));
        entity.setHardConstraintBreaches(result.hardConstraintBreaches().isEmpty() ? null
                : result.hardConstraintBreaches().stream().map(b -> b.name()).reduce((a, b) -> a + "," + b).orElse(null));
        return entity;
    }
}
