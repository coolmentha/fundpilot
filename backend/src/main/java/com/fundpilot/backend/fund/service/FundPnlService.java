package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.DailyChangeResolver;
import com.fundpilot.backend.fund.service.support.DailyChangeResult;
import com.fundpilot.backend.fund.service.support.FundPnlCalculator;
import com.fundpilot.backend.fund.service.support.PortfolioSummary;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import com.fundpilot.backend.market.service.EstimateStatus;
import com.fundpilot.backend.market.service.support.FundMarketDataCapability;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.HashMap;

/**
 * 盈亏与涨跌聚合服务(issue #18,CONTEXT.md「今日涨跌/今日盈亏/总盈亏」)。
 * <p>多表拼装:累计净值用于复权涨跌分析，单位净值用于真实持仓市值和总盈亏。
 *
 * <h3>持仓判定</h3>
 * <ul>
 *   <li>持仓份额为 0 视作无持仓:今日盈亏/总盈亏/持仓市值为 null(但今日涨跌仍算,未建仓基金也看涨跌,story 21)</li>
 *   <li>无净值历史:涨跌/盈亏为 null,持仓份额与成本不依赖净值仍可算</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FundPnlService {

    private final FundPositionService fundPositionService;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final MarketRealtimeCache marketRealtimeCache;
    private final Clock clock;
    private final CurrentActorApi currentActorApi;

    /**
     * 聚合单基金的涨跌与盈亏(issue #38)。
     * <p>普通基金经 {@link DailyChangeResolver} 三态判定(估值前0/当日估值/净值实际)，
     * QDII 仅在最新净值首次发现的北京时间当天使用确认净值结算收益。
     * 今日盈亏 = 最近确认市值 × 今日涨跌幅,总盈亏按最近确认净值 / 当日估值 / 实际净值切换(详见 ADR-0008)。
     *
     * @param fundId 基金 ID
     * @return 六字段(均可为 null,除 isEstimated)封装的 Pnl
     */
    public Pnl computeForFund(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        return fund == null ? emptyPnl() : computeForFund(fund);
    }

    /**
     * 聚合单基金的涨跌与盈亏。调用方已有基金实体时走本重载,避免重复查 fund 表。
     */
    public Pnl computeForFund(FundEntity fund) {
        Long fundId = fund.getId();
        List<FundNavHistoryEntity> latestTwo = fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(fundId);
        BigDecimal rawShares = fundPositionService.getHoldingShares(fundId);
        return computeForFund(fund, latestTwo, rawShares, null, null);
    }

    /** 列表/组合批量聚合，固定三次批量读取，避免逐基金 N+1。 */
    public Map<Long, Pnl> computeForFunds(List<FundEntity> funds) {
        if (funds.isEmpty()) {
            return Map.of();
        }
        List<Long> fundIds = funds.stream().map(FundEntity::getId).toList();
        Map<Long, BigDecimal> sharesByFund = new HashMap<>();
        fundTransactionRepository.aggregateConfirmedShares(fundIds)
                .forEach(row -> sharesByFund.put(row.getFundId(), row.getHoldingShares()));

        Map<Long, List<FundNavHistoryEntity>> navsByFund = new HashMap<>();
        fundNavHistoryRepository.findLatestTwoByFundIds(fundIds).forEach(row -> {
            FundNavHistoryEntity nav = new FundNavHistoryEntity();
            nav.setNavDate(row.getNavDate());
            nav.setNav(row.getNav());
            nav.setAccumulatedNav(row.getAccumulatedNav());
            nav.setFirstSeenAt(row.getFirstSeenAt());
            navsByFund.computeIfAbsent(row.getFundId(), ignored -> new ArrayList<>()).add(nav);
        });
        List<String> fundCodes = funds.stream().map(FundEntity::getFundCode)
                .filter(code -> code != null && !code.isBlank()).distinct().toList();
        Map<String, FundEstimateSnapshot> estimates = fundCodes.isEmpty()
                ? Map.of() : marketRealtimeCache.getEstimates(fundCodes);
        Map<String, EstimateStatus> estimateStatuses = fundCodes.isEmpty()
                ? Map.of() : marketRealtimeCache.getEstimateStatuses(fundCodes);

        Map<Long, Pnl> result = new LinkedHashMap<>();
        for (FundEntity fund : funds) {
            result.put(fund.getId(), computeForFund(
                    fund,
                    navsByFund.getOrDefault(fund.getId(), List.of()),
                    sharesByFund.getOrDefault(fund.getId(), BigDecimal.ZERO),
                    estimates, estimateStatuses));
        }
        return result;
    }

    private Pnl computeForFund(FundEntity fund, List<FundNavHistoryEntity> latestTwo,
                               BigDecimal rawShares, Map<String, FundEstimateSnapshot> batchEstimates,
                               Map<String, EstimateStatus> batchEstimateStatuses) {
        BigDecimal latestAccumulatedNav = latestTwo.size() >= 1 ? latestTwo.get(0).getAccumulatedNav() : null;
        BigDecimal previousAccumulatedNav = latestTwo.size() >= 2 ? latestTwo.get(1).getAccumulatedNav() : null;
        BigDecimal latestUnitNav = latestTwo.size() >= 1 ? latestTwo.get(0).getNav() : null;
        BigDecimal previousUnitNav = latestTwo.size() >= 2 ? latestTwo.get(1).getNav() : null;
        boolean todayNavConfirmed = isTodayNavConfirmed(latestTwo);
        boolean qdii = fund.getInvestmentTarget() == InvestmentTarget.QDII;
        boolean confirmedNavSelected = qdii
                ? latestTwo.size() >= 2 && isLatestNavFirstSeenToday(latestTwo)
                : todayNavConfirmed;
        boolean standardNavSupported = FundMarketDataCapability.supportsStandardNav(fund);

        // QDII 按平台发现日结算；其他日期不复用历史收益，也不混入盘中估值。
        EstimateStatus estimateStatus = confirmedNavSelected ? EstimateStatus.AVAILABLE
                : qdii ? EstimateStatus.STALE
                : !standardNavSupported ? EstimateStatus.UNAVAILABLE
                : getEstimateStatus(fund.getFundCode(), batchEstimateStatuses);
        Optional<FundEstimateSnapshot> estimate = confirmedNavSelected || qdii || !standardNavSupported
                ? Optional.empty()  // 盘后不需要估值
                : getCachedEstimate(fund.getFundCode(), batchEstimates);
        DailyChangeResult changeResult = standardNavSupported || confirmedNavSelected
                ? DailyChangeResolver.resolve(
                        confirmedNavSelected, latestAccumulatedNav, previousAccumulatedNav, estimate, estimateStatus)
                : new DailyChangeResult(null, false);
        BigDecimal dailyChangePct = changeResult.todayChangePct();
        boolean isEstimated = changeResult.isEstimated();
        if (standardNavSupported && dailyChangePct != null && !estimateStatus.isFailure()) {
            estimateStatus = EstimateStatus.AVAILABLE;
        }
        boolean estimateFetchFailed = estimateStatus.isFailure();

        // 持仓份额为 0 视作无持仓:盈亏类字段为 null,但今日涨跌仍返回(观察池基金也看涨跌,story 21)
        BigDecimal holdingShares = rawShares != null && rawShares.signum() != 0 ? rawShares : null;
        BigDecimal costPerShare = holdingShares != null ? fund.getCostPerShare() : null;

        // 今日盈亏 = 昨日市值 × 今日涨跌幅(三态统一口径,不引入单位净值 gsz)
        // 非估计态:dailyChangePct = (latest-previous)/previous,基准是 previousNav
        // 估计态:dailyChangePct = fundgz.gszzl,基准是 latestNav(最新已公布净值)
        boolean usesLatestConfirmedNav = !confirmedNavSelected && !isEstimated && dailyChangePct != null;
        BigDecimal dailyPnlBaseNav = isEstimated || usesLatestConfirmedNav ? latestUnitNav : previousUnitNav;
        BigDecimal dailyPnl = FundPnlCalculator.dailyPnlByChangePct(holdingShares, dailyPnlBaseNav, dailyChangePct);
        // 当日净值已确认时使用实际值;未确认时必须有今日涨跌才能推算当前净值。
        // 估值失败/缺失时不能拿上一期已公布净值冒充当前市值和总盈亏。
        BigDecimal pnlNav = confirmedNavSelected || usesLatestConfirmedNav ? latestUnitNav
                : isEstimated ? estimatedUnitNav(latestUnitNav, dailyChangePct) : null;
        // 当日估值不可用时,总仓位仍按最近确认净值展示;仅今日收益保持未知。
        BigDecimal positionNav = pnlNav != null ? pnlNav : latestUnitNav;
        BigDecimal holdingAmount = computeHoldingAmount(holdingShares, positionNav);
        BigDecimal totalPnl = FundPnlCalculator.totalPnl(holdingShares, positionNav, costPerShare);
        String valuationSource = todayNavConfirmed ? "CONFIRMED_NAV"
                : isEstimated ? "INTRADAY_ESTIMATE"
                : latestUnitNav != null ? "LATEST_CONFIRMED_NAV" : null;
        String estimateTime = estimate.map(FundEstimateSnapshot::estimateTime).orElse(null);
        String baseNavDate = estimate.map(FundEstimateSnapshot::baseNavDate).orElse(null);

        return new Pnl(dailyChangePct, isEstimated, estimateFetchFailed, estimateStatus,
                holdingShares, holdingAmount, dailyPnl, totalPnl, positionNav, valuationSource,
                latestTwo.isEmpty() ? null : latestTwo.get(0).getNavDate(),
                latestTwo.isEmpty() ? null : latestTwo.get(0).getFirstSeenAt(), estimateTime, baseNavDate);
    }

    /** 从实时缓存读取 fundgz 当日估值;缓存未命中降级返 empty。 */
    private Optional<FundEstimateSnapshot> getCachedEstimate(String fundCode) {
        return getCachedEstimate(fundCode, null);
    }

    private Optional<FundEstimateSnapshot> getCachedEstimate(
            String fundCode, Map<String, FundEstimateSnapshot> batchEstimates) {
        if (fundCode == null || fundCode.isBlank()) {
            return Optional.empty();
        }
        Map<String, FundEstimateSnapshot> estimates = batchEstimates != null
                ? batchEstimates : marketRealtimeCache.getEstimates(List.of(fundCode));
        return Optional.ofNullable(estimates.get(fundCode));
    }

    private EstimateStatus getEstimateStatus(String fundCode, Map<String, EstimateStatus> batchStatuses) {
        if (fundCode == null || fundCode.isBlank()) {
            return EstimateStatus.NOT_ATTEMPTED;
        }
        if (batchStatuses != null && batchStatuses.containsKey(fundCode)) {
            return batchStatuses.get(fundCode);
        }
        EstimateStatus status = marketRealtimeCache.getEstimateStatus(fundCode);
        if (status != null) {
            return status;
        }
        return marketRealtimeCache.hasEstimateFetchFailed(fundCode)
                ? EstimateStatus.TIMEOUT : EstimateStatus.NOT_ATTEMPTED;
    }

    /** 当日净值是否已落库:最近一期 navDate 是否达到北京时间今天对应的 UTC 日期标签。 */
    private boolean isTodayNavConfirmed(List<FundNavHistoryEntity> latestTwo) {
        if (latestTwo.isEmpty()) {
            return false;
        }
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());
        Instant latestDate = latestTwo.get(0).getNavDate();
        // navDate 落库为 UTC 0 点,与 today 对齐比较
        return !latestDate.isBefore(today);
    }

    /** QDII 的收益确认日以平台首次发现时间为准，而不是可能滞后的净值日期。 */
    private boolean isLatestNavFirstSeenToday(List<FundNavHistoryEntity> latestTwo) {
        Instant firstSeenAt = latestTwo.get(0).getFirstSeenAt();
        return firstSeenAt != null
                && ChinaTradingDate.toUtcDate(firstSeenAt).equals(ChinaTradingDate.toUtcDate(clock.instant()));
    }

    /** 持仓市值 = 份额 × 最新净值。不乘涨跌幅——净值就是净值,份额锁死。 */
    private BigDecimal computeHoldingAmount(BigDecimal holdingShares, BigDecimal latestNav) {
        if (holdingShares == null || latestNav == null) {
            return null;
        }
        return holdingShares.multiply(latestNav, MathContext.DECIMAL64);
    }

    /** 盘中估算单位净值 = 最新已公布单位净值 × (1 + 今日估算涨跌幅)。 */
    private BigDecimal estimatedUnitNav(BigDecimal latestNav, BigDecimal dailyChangePct) {
        if (latestNav == null || dailyChangePct == null) {
            return null;
        }
        return latestNav.multiply(BigDecimal.ONE.add(dailyChangePct, MathContext.DECIMAL64), MathContext.DECIMAL64);
    }

    /**
     * 聚合所有持仓基金的组合盈亏(issue #18 概览页盈亏 KPI)。
     * <p>遍历 HOLDING 基金,对每只调 {@link #computeForFund},收集三指标列表后调
     * {@link FundPnlCalculator#summarize}。上涨/下跌与盈利/亏损两维度独立(故事 24)。
     *
     * @return 五指标汇总(无持仓基金时全为 0)
     */
    public PortfolioSummary computePortfolioSummary() {
        long userId = currentActorApi.userId();
        List<FundEntity> holdingFunds = fundRepository.findByStatusAndOwnerId(FundStatus.HOLDING, userId);
        Map<Long, Pnl> pnlByFund = computeForFunds(holdingFunds);
        List<BigDecimal> changePcts = new ArrayList<>();
        List<BigDecimal> holdingAmounts = new ArrayList<>();
        List<BigDecimal> dailyPnls = new ArrayList<>();
        List<BigDecimal> totalPnls = new ArrayList<>();
        boolean isEstimated = false;
        int estimateFetchFailedCount = 0;
        for (FundEntity fund : holdingFunds) {
            Pnl pnl = pnlByFund.get(fund.getId());
            changePcts.add(pnl.dailyChangePct());
            holdingAmounts.add(pnl.holdingAmount());
            dailyPnls.add(pnl.dailyPnl());
            totalPnls.add(pnl.totalPnl());
            // 组合只要包含任一盘中估算基金,前端就需要整体标记为估算态。
            isEstimated = isEstimated || pnl.isEstimated();
            if (pnl.estimateFetchFailed()) {
                estimateFetchFailedCount++;
            }
        }
        PortfolioSummary summary = FundPnlCalculator.summarize(changePcts, holdingAmounts, dailyPnls, totalPnls);
        // summarize 是纯数值聚合,估算态来自服务层的三态判定,因此在这里回填。
        return new PortfolioSummary(summary.holdingAmountTotal(), summary.dailyPnlTotal(), summary.dailyChangePct(),
                summary.totalPnlTotal(), summary.holdingFundCount(), summary.dailyCoveredFundCount(),
                summary.risingFundCount(), summary.fallingFundCount(),
                summary.profitableFundCount(), summary.losingFundCount(), isEstimated, estimateFetchFailedCount);
    }

    private Pnl emptyPnl() {
        return new Pnl(null, false, false, EstimateStatus.NOT_ATTEMPTED, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 单基金盈亏结果(字段均可为 null,除 isEstimated;对应 FundView 可空字段)。
     *
     * @param dailyChangePct 今日涨跌幅(三态:估值前0/当日估值/净值实际)
     * @param isEstimated    是否估算态(true=fundgz 估算)
     * @param estimateFetchFailed 当日净值未确认且最近一次估值拉取失败
     * @param estimateStatus 估值或当日净值可用状态
     * @param holdingShares  持仓份额
     * @param holdingAmount  持仓市值(份额 × 最近净值;估算态用昨日净值×(1+涨跌幅)推算)
     * @param dailyPnl       今日盈亏(昨日市值 × 今日涨跌幅)
     * @param totalPnl       总盈亏(盘后用落库净值算 / 盘中估算)
     */
    public record Pnl(
            BigDecimal dailyChangePct,
            boolean isEstimated,
            boolean estimateFetchFailed,
            EstimateStatus estimateStatus,
            BigDecimal holdingShares,
            BigDecimal holdingAmount,
            BigDecimal dailyPnl,
            BigDecimal totalPnl,
            BigDecimal valuationNav,
            String valuationSource,
            Instant valuationDate,
            Instant valuationFirstSeenAt,
            String estimateTime,
            String baseNavDate) {
    }
}
