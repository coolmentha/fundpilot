package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.support.FundPnlCalculator;
import com.fundpilot.backend.fund.service.support.PortfolioSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 聚合单基金的涨跌与盈亏。
     *
     * @param fundId 基金 ID
     * @return 五字段(均可为 null)封装的 Pnl
     */
    public Pnl computeForFund(Long fundId) {
        List<FundNavHistoryEntity> latestTwo = fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(fundId);
        BigDecimal latestNav = latestTwo.size() >= 1 ? latestTwo.get(0).getAccumulatedNav() : null;
        BigDecimal previousNav = latestTwo.size() >= 2 ? latestTwo.get(1).getAccumulatedNav() : null;

        BigDecimal dailyChangePct = FundPnlCalculator.dailyChangePct(latestNav, previousNav);

        // 持仓份额为 0 视作无持仓:盈亏类字段为 null,但今日涨跌仍返回
        BigDecimal rawShares = fundPositionService.getHoldingShares(fundId);
        BigDecimal holdingShares = rawShares != null && rawShares.signum() != 0 ? rawShares : null;
        BigDecimal cost = holdingShares != null ? fundPositionService.getCost(fundId) : null;

        BigDecimal holdingAmount = (holdingShares != null && latestNav != null)
                ? holdingShares.multiply(latestNav) : null;
        BigDecimal dailyPnl = FundPnlCalculator.dailyPnl(holdingShares, latestNav, previousNav);
        BigDecimal totalPnl = FundPnlCalculator.totalPnl(holdingShares, latestNav, cost);

        return new Pnl(dailyChangePct, holdingShares, holdingAmount, dailyPnl, totalPnl);
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
     * 单基金盈亏结果(五字段均可为 null,对应 FundView 可空字段)。
     *
     * @param dailyChangePct 今日涨跌幅
     * @param holdingShares  持仓份额
     * @param holdingAmount  持仓市值(份额 × 最近净值)
     * @param dailyPnl       今日盈亏
     * @param totalPnl       总盈亏
     */
    public record Pnl(
            BigDecimal dailyChangePct,
            BigDecimal holdingShares,
            BigDecimal holdingAmount,
            BigDecimal dailyPnl,
            BigDecimal totalPnl) {
    }
}
