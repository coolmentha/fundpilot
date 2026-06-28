package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.support.DailyChangeResolver;
import com.fundpilot.backend.fund.service.support.DailyChangeResult;
import com.fundpilot.backend.fund.service.support.FundPnlCalculator;
import com.fundpilot.backend.fund.service.support.PortfolioSummary;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.service.FundEstimateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 盈亏与涨跌聚合服务(issue #18,CONTEXT.md「今日涨跌/今日盈亏/总盈亏」)。
 * <p>多表拼装:从 fund_nav_history 取最近两期累计净值,从 FundPositionService 取持仓份额与成本,
 * 调 {@link FundPnlCalculator} 纯函数算涨跌/盈亏。统一用累计净值(分红除权不会让跌幅虚高)。
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
    private final FundEstimateService fundEstimateService;

    /**
     * 聚合单基金的涨跌与盈亏(三态,issue #38)。
     * <p>今日涨跌经 {@link DailyChangeResolver} 三态判定(盘前0/盘中估值/盘后实际),
     * 今日盈亏 = 昨日市值 × 今日涨跌幅,总盈亏盘后用落库净值算 / 盘中估算(详见 PRD #34 / ADR-0008)。
     *
     * @param fundId 基金 ID
     * @return 六字段(均可为 null,除 isEstimated)封装的 Pnl
     */
    public Pnl computeForFund(Long fundId) {
        List<FundNavHistoryEntity> latestTwo = fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(fundId);
        BigDecimal latestNav = latestTwo.size() >= 1 ? latestTwo.get(0).getAccumulatedNav() : null;
        BigDecimal previousNav = latestTwo.size() >= 2 ? latestTwo.get(1).getAccumulatedNav() : null;
        boolean todayNavConfirmed = isTodayNavConfirmed(latestTwo);

        // 三态判定:盘后(当日净值落库)用落库净值;盘中(未落库)按需拉 fundgz 估值
        Optional<FundEstimateSnapshot> estimate = todayNavConfirmed
                ? Optional.empty()  // 盘后不需要估值
                : fetchEstimate(fundId);
        DailyChangeResult changeResult = DailyChangeResolver.resolve(
                Instant.now(), todayNavConfirmed, latestNav, previousNav, estimate);
        BigDecimal dailyChangePct = changeResult.todayChangePct();
        boolean isEstimated = changeResult.isEstimated();

        // 持仓份额为 0 视作无持仓:盈亏类字段为 null,但今日涨跌仍返回(观察池基金也看涨跌,story 21)
        BigDecimal rawShares = fundPositionService.getHoldingShares(fundId);
        BigDecimal holdingShares = rawShares != null && rawShares.signum() != 0 ? rawShares : null;
        BigDecimal costPerShare = holdingShares != null
                ? fundRepository.findById(fundId).map(FundEntity::getCostPerShare).orElse(null)
                : null;

        // 今日盈亏 = 昨日市值 × 今日涨跌幅(三态统一口径,不引入单位净值 gsz)
        // 非估计态:dailyChangePct = (latest-previous)/previous,基准是 previousNav
        // 估计态:dailyChangePct = fundgz.gszzl,基准是 latestNav(最新已公布净值)
        BigDecimal dailyPnlBaseNav = isEstimated ? latestNav : previousNav;
        BigDecimal dailyPnl = FundPnlCalculator.dailyPnlByChangePct(holdingShares, dailyPnlBaseNav, dailyChangePct);
        // 持仓市值 = 份额 × 最新净值(不做盘中估算修正)
        BigDecimal holdingAmount = computeHoldingAmount(holdingShares, latestNav);
        // 总盈亏 = 份额 × (最新净值 - 成本单价),不乘涨跌幅(净值就是净值)
        BigDecimal totalPnl = FundPnlCalculator.totalPnl(holdingShares, latestNav, costPerShare);

        return new Pnl(dailyChangePct, isEstimated, holdingShares, holdingAmount, dailyPnl, totalPnl);
    }

    /** 拉取 fundgz 盘中估值(基金实体查 code);失败降级返 empty。 */
    private Optional<FundEstimateSnapshot> fetchEstimate(Long fundId) {
        return fundRepository.findById(fundId)
                .map(FundEntity::getFundCode)
                .flatMap(fundEstimateService::fetchEstimate);
    }

    /** 当日净值是否已落库:最近一期 navDate 是否 = 今天(UTC)。 */
    private boolean isTodayNavConfirmed(List<FundNavHistoryEntity> latestTwo) {
        if (latestTwo.isEmpty()) {
            return false;
        }
        Instant today = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant latestDate = latestTwo.get(0).getNavDate();
        // navDate 落库为 UTC 0 点,与 today 对齐比较
        return !latestDate.isBefore(today);
    }

    /** 持仓市值 = 份额 × 最新净值。不乘涨跌幅——净值就是净值,份额锁死。 */
    private BigDecimal computeHoldingAmount(BigDecimal holdingShares, BigDecimal latestNav) {
        if (holdingShares == null || latestNav == null) {
            return null;
        }
        return holdingShares.multiply(latestNav, MathContext.DECIMAL64);
    }

    /**
     * 聚合所有持仓基金的组合盈亏(issue #18 概览页盈亏 KPI)。
     * <p>遍历 HOLDING 基金,对每只调 {@link #computeForFund},收集三指标列表后调
     * {@link FundPnlCalculator#summarize}。上涨/下跌与盈利/亏损两维度独立(故事 24)。
     *
     * @return 五指标汇总(无持仓基金时全为 0)
     */
    public PortfolioSummary computePortfolioSummary() {
        List<FundEntity> holdingFunds = fundRepository.findByStatus(FundStatus.HOLDING);
        List<BigDecimal> changePcts = new ArrayList<>();
        List<BigDecimal> dailyPnls = new ArrayList<>();
        List<BigDecimal> totalPnls = new ArrayList<>();
        for (FundEntity fund : holdingFunds) {
            Pnl pnl = computeForFund(fund.getId());
            changePcts.add(pnl.dailyChangePct());
            dailyPnls.add(pnl.dailyPnl());
            totalPnls.add(pnl.totalPnl());
        }
        return FundPnlCalculator.summarize(changePcts, dailyPnls, totalPnls);
    }

    /**
     * 单基金盈亏结果(字段均可为 null,除 isEstimated;对应 FundView 可空字段)。
     *
     * @param dailyChangePct 今日涨跌幅(三态:盘前0/盘中估值/盘后实际)
     * @param isEstimated    是否估算态(true=盘中 fundgz 估算)
     * @param holdingShares  持仓份额
     * @param holdingAmount  持仓市值(份额 × 最近净值;估算态用昨日净值×(1+涨跌幅)推算)
     * @param dailyPnl       今日盈亏(昨日市值 × 今日涨跌幅)
     * @param totalPnl       总盈亏(盘后用落库净值算 / 盘中估算)
     */
    public record Pnl(
            BigDecimal dailyChangePct,
            boolean isEstimated,
            BigDecimal holdingShares,
            BigDecimal holdingAmount,
            BigDecimal dailyPnl,
            BigDecimal totalPnl) {
    }
}
