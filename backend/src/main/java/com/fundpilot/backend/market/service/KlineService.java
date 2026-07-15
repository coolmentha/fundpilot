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
import com.fundpilot.backend.market.entity.IndexKlineEntity;
import com.fundpilot.backend.market.repository.IndexKlineRepository;
import com.fundpilot.backend.market.service.support.SecidFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * K 线/走势图数据服务(行情工作台基金详情页)。
 *
 * <p>按 {@link FundSubType} 分派数据源:
 * <ul>
 *   <li>ETF / INDEX / INDEX_ENHANCED:有 {@code benchmarkIndexCode} 时优先读 {@code index_kline} 本地缓存
 *       (MarketDataFetchService 每日算 VolumeState 时顺便落库),渲染日/周/月 K(周/月在日 K 上聚合)。
 *       缓存为空(尚未同步)时降级实时拉 push2his(可能被 IP 限流,失败再降级净值走势)。</li>
 *   <li>ACTIVE / MIXED 或无 benchmarkIndexCode:读本地 fund_nav_history 累计净值,渲染净值走势折线图。</li>
 * </ul>
 *
 * <p>读本地缓存是主路径——避免图表按需拉 push2his 触发 IP 限流("Unexpected end of file")。
 * 周/月 K 由日 K 聚合(open=首日、high=max、low=min、close=末日、volume=sum),不单独存。
 */
@Service
@RequiredArgsConstructor
public class KlineService {

    private static final Logger log = LoggerFactory.getLogger(KlineService.class);

    /** period → klt 映射(东方财富 K 线周期参数),仅缓存为空时的实时降级用。 */
    private static final String KLT_DAILY = "101";
    private static final String KLT_WEEKLY = "102";
    private static final String KLT_MONTHLY = "103";
    private static final String KLINE_LIMIT = "400";

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final IndexKlineRepository indexKlineRepository;
    private final MarketDataSource marketDataSource;

    public KlineView getKline(Long fundId, String period) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "基金不存在: " + fundId));

        if (isIndexLike(fund.getFundSubType()) && fund.getBenchmarkIndexCode() != null) {
            String indexCode = fund.getBenchmarkIndexCode();
            // 1. 主路径:读本地日 K 缓存,聚合出日/周/月 K
            List<IndexKlineEntity> cached = indexKlineRepository.findByIndexCodeOrderByTradeDateAsc(indexCode);
            if (!cached.isEmpty()) {
                return new KlineView("kline", indexCode, aggregate(cached, period));
            }
            // 2. 缓存为空(尚未同步):实时拉 push2his 兜底(用 period 对应 klt)
            Optional<String> secid = SecidFormat.fromIndexCode(indexCode);
            if (secid.isPresent()) {
                try {
                    IndexKline kline = marketDataSource.fetchIndexKlineWithPeriod(
                            secid.get(), mapPeriod(period), KLINE_LIMIT);
                    return new KlineView("kline", indexCode,
                            kline.bars().stream()
                                    .map(b -> new KlineView.Bar(b.date(), b.open(), b.close(),
                                            b.high(), b.low(), b.volume()))
                                    .toList());
                } catch (RuntimeException e) {
                    log.warn("基金 {} 指数 K 线实时拉取失败({}),降级为净值走势: {}",
                            fundId, indexCode, e.getMessage());
                }
            }
        }
        // 主动基金 / 降级:读本地净值历史
        return buildNavView(fund);
    }

    /**
     * 把日 K 缓存按 period 聚合。daily 原样;weekly 按周一分组;monthly 按月首分组。
     * 每组:open=首日 open、high=max、low=min、close=末日 close、volume=sum,date=末日(蜡烛绘在周期末)。
     */
    private List<KlineView.Bar> aggregate(List<IndexKlineEntity> daily, String period) {
        if (period == null || "daily".equalsIgnoreCase(period) || "d".equalsIgnoreCase(period)) {
            return daily.stream().map(KlineService::toBar).toList();
        }
        Map<Instant, List<IndexKlineEntity>> groups = new LinkedHashMap<>();
        boolean weekly = "weekly".equalsIgnoreCase(period) || "w".equalsIgnoreCase(period);
        for (IndexKlineEntity e : daily) {
            var d = e.getTradeDate().atZone(ZoneOffset.UTC);
            Instant key = (weekly ? d.with(DayOfWeek.MONDAY) : d.withDayOfMonth(1))
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    .toInstant();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<KlineView.Bar> result = new ArrayList<>(groups.size());
        for (List<IndexKlineEntity> group : groups.values()) {
            IndexKlineEntity first = group.get(0);
            IndexKlineEntity last = group.get(group.size() - 1);
            BigDecimal high = group.stream().map(IndexKlineEntity::getHigh)
                    .filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
            BigDecimal low = group.stream().map(IndexKlineEntity::getLow)
                    .filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
            long vol = group.stream().mapToLong(g -> g.getVolume() == null ? 0 : g.getVolume()).sum();
            result.add(new KlineView.Bar(last.getTradeDate(), first.getOpen(), last.getClose(), high, low, vol));
        }
        return result;
    }

    private static KlineView.Bar toBar(IndexKlineEntity e) {
        return new KlineView.Bar(e.getTradeDate(), e.getOpen(), e.getClose(),
                e.getHigh(), e.getLow(), e.getVolume() == null ? 0L : e.getVolume());
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
