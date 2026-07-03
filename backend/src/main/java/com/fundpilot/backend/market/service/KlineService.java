package com.fundpilot.backend.market.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.controller.KlineView;
import com.fundpilot.backend.market.service.support.SecidFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * K 线/走势图数据服务(行情工作台基金详情页)。
 *
 * <p>按 {@link FundSubType} 分派数据源:
 * <ul>
 *   <li>ETF / INDEX / INDEX_ENHANCED:拉 {@code benchmarkIndexCode} 的指数 K 线(OHLCV),
 *       前端渲染蜡烛图 + 成交量柱。周期参数 period → klt(daily=101/weekly=102/monthly=103)。</li>
 *   <li>ACTIVE / MIXED 或无 benchmarkIndexCode:读本地 fund_nav_history 累计净值,
 *       前端渲染净值走势折线图。</li>
 * </ul>
 *
 * <p>K 线按需拉取不缓存(用户主动查看,数据量大);净值走势读已落库历史(信号引擎每日拉的副产品)。
 */
@Service
@RequiredArgsConstructor
public class KlineService {

    private static final Logger log = LoggerFactory.getLogger(KlineService.class);

    /** period → klt 映射(东方财富 K 线周期参数)。 */
    private static final String KLT_DAILY = "101";
    private static final String KLT_WEEKLY = "102";
    private static final String KLT_MONTHLY = "103";
    private static final String KLINE_LIMIT = "400";

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final MarketDataSource marketDataSource;

    @Transactional(readOnly = true)
    public KlineView getKline(Long fundId, String period) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "基金不存在: " + fundId));

        String klt = mapPeriod(period);
        // ETF/指数基金:有 benchmarkIndexCode 时走指数 K 线
        if (isIndexLike(fund.getFundSubType()) && fund.getBenchmarkIndexCode() != null) {
            Optional<String> secid = SecidFormat.fromIndexCode(fund.getBenchmarkIndexCode());
            if (secid.isPresent()) {
                try {
                    IndexKline kline = marketDataSource.fetchIndexKlineWithPeriod(secid.get(), klt, KLINE_LIMIT);
                    return new KlineView("kline", fund.getBenchmarkIndexCode(),
                            kline.bars().stream()
                                    .map(b -> new KlineView.Bar(b.date(), b.open(), b.close(),
                                            b.high(), b.low(), b.volume()))
                                    .toList());
                } catch (RuntimeException e) {
                    log.warn("基金 {} 指数 K 线拉取失败({}),降级为净值走势: {}",
                            fundId, fund.getBenchmarkIndexCode(), e.getMessage());
                }
            }
        }
        // 主动基金 / 降级:读本地净值历史
        return buildNavView(fund);
    }

    private KlineView buildNavView(FundEntity fund) {
        List<FundNavHistoryEntity> history = fundNavHistoryRepository.findByFundEntity_Id(fund.getId());
        return new KlineView("nav", fund.getBenchmarkIndexCode(),
                history.stream()
                        .map(n -> new KlineView.Bar(n.getNavDate(), null, n.getAccumulatedNav(),
                                null, null, 0L))
                        .toList());
    }

    private boolean isIndexLike(FundSubType subType) {
        return subType == FundSubType.ETF || subType == FundSubType.INDEX
                || subType == FundSubType.INDEX_ENHANCED;
    }

    private String mapPeriod(String period) {
        if (period == null) return KLT_DAILY;
        return switch (period.toLowerCase()) {
            case "weekly", "w", "week" -> KLT_WEEKLY;
            case "monthly", "m", "month" -> KLT_MONTHLY;
            default -> KLT_DAILY;
        };
    }
}
