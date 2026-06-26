package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.fund.service.support.WeeklyDropCalculator;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.MeasureUnit;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.signal.valueobject.Measure;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.DisciplineStrategyService;
import com.fundpilot.backend.strategy.service.support.CapitalContext;
import com.fundpilot.backend.strategy.service.support.MarketIndicators;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class SignalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundStrategyRepository fundStrategyRepository;
    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final FundPositionService fundPositionService;
    private final MarketIndicatorProvider marketIndicatorProvider;
    private final SignalLogRepository signalLogRepository;
    private final UserConfigRepository userConfigRepository;
    private final TradingCalendarService tradingCalendarService;
    private final DisciplineStrategyService disciplineStrategyService;

    public SignalGenerationService(FundStrategyRepository fundStrategyRepository,
                                   FundRepository fundRepository,
                                   FundNavHistoryRepository fundNavHistoryRepository,
                                   FundTransactionRepository fundTransactionRepository,
                                   FundPositionService fundPositionService,
                                   MarketIndicatorProvider marketIndicatorProvider,
                                   SignalLogRepository signalLogRepository,
                                   UserConfigRepository userConfigRepository,
                                   TradingCalendarService tradingCalendarService,
                                   DisciplineStrategyService disciplineStrategyService) {
        this.fundStrategyRepository = fundStrategyRepository;
        this.fundRepository = fundRepository;
        this.fundNavHistoryRepository = fundNavHistoryRepository;
        this.fundTransactionRepository = fundTransactionRepository;
        this.fundPositionService = fundPositionService;
        this.marketIndicatorProvider = marketIndicatorProvider;
        this.signalLogRepository = signalLogRepository;
        this.userConfigRepository = userConfigRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.disciplineStrategyService = disciplineStrategyService;
    }

    /**
     * 生成指定日期的全量信号。每只 EFFECTIVE 基金落一行 SignalLog(含 NONE 兜底)。
     * 单只基金异常不影响其他基金。
     */
    @Transactional
    public void generateDailySignals(LocalDate date) {
        List<Long> fundIds = fundStrategyRepository.findEffectiveFundIds();
        BigDecimal totalInvestableCapital = userConfigRepository.findAll().stream()
                .map(UserConfigEntity::getTotalInvestableCapital)
                .findFirst().orElse(BigDecimal.ZERO);
        for (Long fundId : fundIds) {
            try {
                generateForFund(fundId, date, totalInvestableCapital);
            } catch (RuntimeException ex) {
                log.error("信号生成失败 fund_id={} date={}: {}", fundId, date, ex.getMessage(), ex);
            }
        }
    }

    private void generateForFund(Long fundId, LocalDate date, BigDecimal totalInvestableCapital) {
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

        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Optional<MarketIndicatorSnapshotEntity> snapshotOpt = marketIndicatorProvider.getIndicators(fundId, date);
        SignalResult result;
        if (snapshotOpt.isEmpty()) {
            result = SignalResult.none("INSUFFICIENT_MARKET_DATA");
        } else {
            MarketIndicators market = toMarketIndicators(snapshotOpt.get());
            CapitalContext capital = buildCapitalContext(fund, strategy, market, totalInvestableCapital, date);
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
                                                MarketIndicators market, BigDecimal totalInvestableCapital,
                                                LocalDate date) {
        BigDecimal currentNav = market.currentNav() != null ? market.currentNav() : BigDecimal.ZERO;
        BigDecimal peakNav = fundNavHistoryRepository.findPeakAccumulatedNav(fund.getId()).orElse(currentNav);
        BigDecimal holdingPeakNav = fund.getOpenedAt() != null
                ? fundNavHistoryRepository.findPeakAccumulatedNavSince(fund.getId(), fund.getOpenedAt()).orElse(currentNav)
                : peakNav;
        BigDecimal holdingShares = fundPositionService.getHoldingShares(fund.getId());
        BigDecimal holdingAmount = holdingShares.multiply(currentNav, MATH);
        BigDecimal totalEquityAmount = computeTotalEquityAmount(currentNav);
        BigDecimal singlePositionPct = totalEquityAmount.signum() > 0
                ? holdingAmount.divide(totalEquityAmount, MATH) : BigDecimal.ZERO;
        BigDecimal categoryPositionPct = computeCategoryPositionPct(fund.getFundCategory(), currentNav, totalEquityAmount);
        BigDecimal totalEquityPct = totalInvestableCapital.signum() > 0
                ? totalEquityAmount.divide(totalInvestableCapital, MATH) : BigDecimal.ZERO;
        BigDecimal buildShares = sumShares(fundTransactionRepository
                .findByFundEntity_IdAndSignalLogEntity_SignalTypeAndStatus(fund.getId(), SignalType.BUILD, FundTransactionStatus.CONFIRMED));
        Map<Integer, BigDecimal> tierAddShares = buildTierAddShares(fund.getId());
        Instant lastBuyConfirmTime = computeLastBuyConfirmTime(fund, strategy);

        return new CapitalContext(peakNav, holdingPeakNav, singlePositionPct, categoryPositionPct,
                totalEquityPct, totalEquityAmount, fund.getPlannedTotalAmount() != null ? fund.getPlannedTotalAmount() : BigDecimal.ZERO,
                buildShares, tierAddShares, holdingShares, lastBuyConfirmTime);
    }

    /** 总权益持仓金额 = 所有基金 CONFIRMED 持仓份额 × 各自最近净值 之和。 */
    private BigDecimal computeTotalEquityAmount(BigDecimal fallbackNav) {
        List<Long> allFundIds = fundStrategyRepository.findEffectiveFundIds();
        BigDecimal sum = BigDecimal.ZERO;
        for (Long fid : allFundIds) {
            BigDecimal shares = fundPositionService.getHoldingShares(fid);
            BigDecimal nav = fundNavHistoryRepository.findTop5ByFundEntity_IdOrderByNavDateDesc(fid).stream()
                    .findFirst().map(FundNavHistoryEntity::getAccumulatedNav).orElse(fallbackNav);
            sum = sum.add(shares.multiply(nav, MATH), MATH);
        }
        return sum;
    }

    /** 单类基金占比 = 该 category 所有基金持仓金额 / 总权益。 */
    private BigDecimal computeCategoryPositionPct(com.fundpilot.backend.fund.enums.FundCategory category,
                                                   BigDecimal fallbackNav, BigDecimal totalEquityAmount) {
        if (totalEquityAmount.signum() <= 0 || category == null) {
            return BigDecimal.ZERO;
        }
        List<Long> allFundIds = fundStrategyRepository.findEffectiveFundIds();
        BigDecimal categorySum = BigDecimal.ZERO;
        for (Long fid : allFundIds) {
            FundEntity f = fundRepository.findById(fid).orElse(null);
            if (f == null || f.getFundCategory() != category) {
                continue;
            }
            BigDecimal shares = fundPositionService.getHoldingShares(fid);
            BigDecimal nav = fundNavHistoryRepository.findTop5ByFundEntity_IdOrderByNavDateDesc(fid).stream()
                    .findFirst().map(FundNavHistoryEntity::getAccumulatedNav).orElse(fallbackNav);
            categorySum = categorySum.add(shares.multiply(nav, MATH), MATH);
        }
        return categorySum.divide(totalEquityAmount, MATH);
    }

    /** 各档加仓份额 map(key 1~4):查 signalLog.signalType=ADD AND triggerTier=N AND status=CONFIRMED 的交易 shares。 */
    private Map<Integer, BigDecimal> buildTierAddShares(Long fundId) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (int tier = 1; tier <= 4; tier++) {
            List<FundTransactionEntity> txs = fundTransactionRepository
                    .findByFundEntity_IdAndSignalLogEntity_SignalTypeAndSignalLogEntity_TriggerTierAndStatus(
                            fundId, SignalType.ADD, tier, FundTransactionStatus.CONFIRMED);
            map.put(tier, sumShares(txs));
        }
        return map;
    }

    private static BigDecimal sumShares(List<FundTransactionEntity> txs) {
        return txs.stream().map(FundTransactionEntity::getShares)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 最近一次买入确认时间 = max(openedAt, tier1-4AddedAt)。 */
    private Instant computeLastBuyConfirmTime(FundEntity fund, FundStrategyEntity strategy) {
        Instant latest = fund.getOpenedAt();
        for (int tier = 1; tier <= 4; tier++) {
            Instant t = switch (tier) {
                case 1 -> strategy.getTier1AddedAt();
                case 2 -> strategy.getTier2AddedAt();
                case 3 -> strategy.getTier3AddedAt();
                case 4 -> strategy.getTier4AddedAt();
                default -> null;
            };
            if (t != null && (latest == null || t.isAfter(latest))) {
                latest = t;
            }
        }
        return latest;
    }

    private long computeTradingDaysSinceLastBuy(FundEntity fund, FundStrategyEntity strategy, LocalDate today) {
        Instant lastBuy = computeLastBuyConfirmTime(fund, strategy);
        if (lastBuy == null) {
            return HardConstraintConfigHolder.MIN_HOLD_DAYS + 1; // 无买入记录视为已满窗口
        }
        LocalDate from = lastBuy.atZone(ZoneOffset.UTC).toLocalDate();
        return tradingCalendarService.daysBetweenTradingDays(from, today);
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
        entity.setWarnings(result.warnings().isEmpty() ? null : String.join(",", result.warnings()));
        entity.setHardConstraintBreaches(result.hardConstraintBreaches().isEmpty() ? null
                : result.hardConstraintBreaches().stream().map(b -> b.name()).reduce((a, b) -> a + "," + b).orElse(null));
        return entity;
    }

    /** HardConstraintConfig 的 holder(避免循环依赖,直接引用常量)。 */
    private static final class HardConstraintConfigHolder {
        static final int MIN_HOLD_DAYS = com.fundpilot.backend.fund.service.support.HardConstraintConfig.MIN_HOLD_DAYS;
    }
}
