package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyJsParser;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyPush2Client;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundIntradayChart;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.IndexRealtimeSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MarketBreadthSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MarketLimitCounts;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MoneyFlowSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.SectorSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsIndexFlashClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsJsParser;
import com.fundpilot.backend.marketdata.adapter.api.watchedindex.WatchedIndicesApi;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 行情实时数据缓存(行情工作台核心)。
 *
 * <p>解决「前端 5-10s 高频轮询 vs 东方财富共享限流」的矛盾:本服务以 30s 周期
 * 从东方财富拉数据填入内存并写穿 Redis,前端轮询只读内存不触外部请求。
 *
 * <p>五类缓存:
 * <ul>
 *   <li>{@link #indexCache} 指数实时行情(用户关注列表,30s 刷新)</li>
 *   <li>{@link #breadthCache} 沪深京股票涨跌家数(30s 刷新)</li>
 *   <li>{@link #sectorCache} 行业板块涨跌 + 主力资金(30s 刷新)</li>
 *   <li>{@link #moneyFlowCache} 北向资金(30s 刷新)</li>
 *   <li>{@link #estimateCache} 基金当日估值(主要市场覆盖窗口 30s 刷新 + 启动异步预热,N 只基金逐个拉)</li>
 * </ul>
 *
 * <p>降级策略:指数/市场宽度/板块/资金刷新失败保留旧缓存。基金估值不同:它是当天短时态数据,
 * 单只拉取失败、空响应或日期过期时必须立即删除旧估值并标记失败,禁止把旧估值继续作为今日数据。
 */
@Service
@RequiredArgsConstructor
public class MarketRealtimeCache {

    private static final Logger log = LoggerFactory.getLogger(MarketRealtimeCache.class);
    private static final DateTimeFormatter ESTIMATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration ESTIMATE_FAILURE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration ESTIMATE_REFRESH_BUDGET = Duration.ofSeconds(25);
    private static final List<String> MARKET_BREADTH_SECIDS = List.of(
            "1.000001", // 上证指数：沪市宽度
            "0.399001", // 深证成指：深市宽度
            "0.899050"  // 北证 50：北交所宽度
    );

    private final EastmoneyPush2Client push2Client;
    private final FundEstimateService fundEstimateService;
    private final WatchedIndicesApi watchedIndicesApi;
    private final TrackedNavProductGateway products;
    private final MarketDataMetrics marketDataMetrics;
    private final Clock clock;
    private final MarketRealtimeRedisStore redisStore;
    private final ThsIndexFlashClient thsIndexFlashClient;

    // volatile 保证可见性;定时刷新单线程写,前端读线程只读,无需加锁
    private volatile List<IndexRealtimeSnapshot> indexCache = List.of();
    private volatile MarketBreadthSnapshot breadthCache = null;
    private volatile List<SectorSnapshot> sectorCache = List.of();
    private volatile MoneyFlowSnapshot moneyFlowCache = null;
    private final Map<String, FundEstimateSnapshot> estimateCache = new ConcurrentHashMap<>();
    private final Map<String, FundIntradayChart> intradayCache = new ConcurrentHashMap<>();
    private final Map<String, EstimateStatus> estimateStatuses = new ConcurrentHashMap<>();
    private final Map<String, Instant> estimateRetryAfter = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshingEstimates = new AtomicBoolean(false);
    private int allEstimateCursor;
    private int qdiiEstimateCursor;

    @PostConstruct
    void restoreFromRedis() {
        redisStore.load().ifPresent(snapshot -> {
            indexCache = snapshot.indices() == null ? List.of() : List.copyOf(snapshot.indices());
            MarketBreadthSnapshot restoredBreadth = snapshot.breadth();
            breadthCache = restoredBreadth != null && restoredBreadth.hasLimitCounts() ? restoredBreadth : null;
            sectorCache = snapshot.sectors() == null ? List.of() : List.copyOf(snapshot.sectors());
            moneyFlowCache = snapshot.moneyFlow();
            if (snapshot.estimateStatuses() != null) {
                estimateStatuses.putAll(snapshot.estimateStatuses());
            }
            if (snapshot.estimates() != null) {
                snapshot.estimates().forEach((code, estimate) -> {
                    EstimateStatus status = classifyFreshness(FundEstimateResult.available(estimate));
                    if (status == EstimateStatus.AVAILABLE) {
                        estimateCache.put(code, estimate);
                    } else {
                        estimateStatuses.put(code, status);
                    }
                });
            }
            if (snapshot.intradayCharts() != null) {
                snapshot.intradayCharts().forEach((code, chart) -> {
                    if (estimateCache.containsKey(code) && chart != null && chart.points().size() >= 2) {
                        intradayCache.put(code, chart);
                    }
                });
            }
            log.info("已从 Redis 恢复行情缓存");
        });
    }

    /** 读指数缓存(已按用户关注列表过滤,顺序按请求 secid 顺序)。 */
    public List<IndexRealtimeSnapshot> getIndices() {
        return indexCache;
    }

    public List<IndexRealtimeSnapshot> getIndices(List<String> indexCodes) {
        Map<String, IndexRealtimeSnapshot> byCode = indexCache.stream()
                .collect(Collectors.toMap(IndexRealtimeSnapshot::secid, Function.identity(), (first, ignored) -> first));
        return indexCodes.stream().map(byCode::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 读沪深京股票市场宽度缓存。 */
    public MarketBreadthSnapshot getBreadth() {
        return breadthCache;
    }

    /** 读板块缓存(东方财富返回顺序,通常按涨幅降序)。 */
    public List<SectorSnapshot> getSectors() {
        return sectorCache;
    }

    /** 读北向资金缓存。 */
    public MoneyFlowSnapshot getMoneyFlow() {
        return moneyFlowCache;
    }

    /**
     * 批量读基金估值缓存,不在请求链路实时拉取。
     * @param fundCodes 基金代码列表
     * @return code → 估值快照;缓存未命中的 code 不出现在 map 中
     */
    public Map<String, FundEstimateSnapshot> getEstimates(List<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Map.of();
        }
        return fundCodes.stream()
                .filter(estimateCache::containsKey)
                .collect(Collectors.toMap(Function.identity(), estimateCache::get));
    }

    /** Reads a cached intraday chart by product code without invoking an external source. */
    public FundIntradayChart getIntraday(String fundCode) {
        return fundCode == null ? null : intradayCache.get(fundCode);
    }

    /** 当前进程最近一次刷新该基金估值是否失败。 */
    public boolean hasEstimateFetchFailed(String fundCode) {
        return getEstimateStatus(fundCode).isFailure();
    }

    public EstimateStatus getEstimateStatus(String fundCode) {
        return fundCode == null ? EstimateStatus.NOT_ATTEMPTED
                : estimateStatuses.getOrDefault(fundCode, EstimateStatus.NOT_ATTEMPTED);
    }

    public Map<String, EstimateStatus> getEstimateStatuses(List<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Map.of();
        }
        return fundCodes.stream().collect(Collectors.toMap(Function.identity(), this::getEstimateStatus));
    }

    /**
     * 全量刷新五类缓存——由 MarketData 的实时估值调度在交易时段调用。
     * 任一类失败不影响其他类(独立 try-catch)。外部请求不由数据库事务包裹。
     */
    public void refreshAll() {
        refreshIndices();
        refreshSectors();
        refreshMoneyFlow();
        refreshFundEstimates();
    }

    /**
     * 仅刷新指数、市场宽度、板块、资金四类(不含基金估值)。
     */
    public void refreshRealtimeWithoutEstimates() {
        refreshIndices();
        refreshSectors();
        refreshMoneyFlow();
    }

    /**
     * 应用启动时预热指数/板块/资金缓存,不在启动线程逐只刷新基金估值。
     *
     * <p>修复 bug:定时任务仅在交易时段运行，
     * 仅交易时段(MON-FRI 9:30-15:00)跑,部署发生在非交易时段(周末/盘后/盘前)时
     * {@code indexCache} 初始空,工作台显示「暂无关注指数」直到用户重新配置触发
     * 关注指数变更。启动时刷一次,盘后/周末也能展示收盘数据
     * (东方财富盘后返回收盘值)。同一请求还会预热固定沪深京市场宽度。基金估值请求数随基金数量增长，
     * 由独立异步监听器预热，避免 ApplicationReadyEvent 阻塞应用启动。
     *
     * <p>刷新失败不阻塞启动:记 warn,前端显示空态直到下次定时刷新。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        try {
            refreshRealtimeWithoutEstimates();
            log.info("行情缓存启动刷新完成(指数/市场宽度/板块/资金),基金估值由后台异步预热");
        } catch (RuntimeException e) {
            log.warn("行情缓存启动刷新失败,前端将显示空态直到下次定时刷新", e);
        }
    }

    /**
     * 应用启动完成后异步预热全部基金估值。
     * <p>复用 Spring 异步执行与东方财富共享限流,避免 N 只基金请求阻塞健康检查。
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmFundEstimatesAfterReady() {
        refreshFundEstimates();
        log.info("基金估值缓存异步启动预热完成");
    }

    public void refreshIndices() {
        long startedAt = System.nanoTime();
        String result = "success";
        try {
            List<String> watchedSecids = watchedIndicesApi.findAllForRefresh();
            Set<String> requestedSecids = new LinkedHashSet<>(watchedSecids);
            requestedSecids.addAll(MARKET_BREADTH_SECIDS);

            List<String> requestOrder = List.copyOf(requestedSecids);
            String raw = push2Client.fetchIndexRealtimeRaw(String.join(",", requestOrder));
            Map<String, IndexRealtimeSnapshot> snapshotsBySecid = EastmoneyJsParser
                    .parseIndexRealtime(raw, requestOrder).stream()
                    .collect(Collectors.toMap(IndexRealtimeSnapshot::secid, Function.identity(),
                            (first, ignored) -> first));
            // 逐指数合并：缺失的 secid 保留旧快照(与「刷新失败保留旧缓存」降级一致)，
            // 只有整体缺失才整体保留旧缓存
            boolean indicesUpdated = false;
            if (!snapshotsBySecid.isEmpty()) {
                Map<String, IndexRealtimeSnapshot> previousBySecid = indexCache.stream()
                        .collect(Collectors.toMap(IndexRealtimeSnapshot::secid, Function.identity(),
                                (first, ignored) -> first));
                indexCache = watchedSecids.stream()
                        .map(secid -> snapshotsBySecid.getOrDefault(secid, previousBySecid.get(secid)))
                        .filter(java.util.Objects::nonNull)
                        .toList();
                indicesUpdated = true;
            }

            MarketBreadthSnapshot breadth = EastmoneyJsParser.parseMarketBreadth(raw, MARKET_BREADTH_SECIDS);
            MarketLimitCounts limits = fetchMarketLimitCounts();
            boolean breadthUpdated = breadth != null && limits != null;
            if (breadthUpdated) {
                breadthCache = new MarketBreadthSnapshot(
                        breadth.risingCount(), breadth.fallingCount(),
                        limits.limitUpCount(), limits.limitDownCount());
            }
            if (snapshotsBySecid.isEmpty() && !breadthUpdated) {
                result = "empty";
            }
            if (indicesUpdated || breadthUpdated) {
                persist();
            }
        } catch (RuntimeException e) {
            result = metricResult(e);
            log.warn("指数实时行情与市场宽度刷新失败,保留旧缓存", e);
        } finally {
            marketDataMetrics.record("EastmoneyPush2Client", "fetchIndices", result, startedAt);
        }
    }

    private void refreshSectors() {
        long startedAt = System.nanoTime();
        String result = "success";
        try {
            String raw = push2Client.fetchSectorListRaw("f3");
            List<SectorSnapshot> parsed = EastmoneyJsParser.parseSectorList(raw);
            if (parsed.isEmpty()) {
                result = "empty";
            } else {
                sectorCache = parsed;
                persist();
            }
        } catch (RuntimeException e) {
            result = metricResult(e);
            log.warn("行业板块刷新失败,保留旧缓存", e);
        } finally {
            marketDataMetrics.record("EastmoneyPush2Client", "fetchSectors", result, startedAt);
        }
    }

    private MarketLimitCounts fetchMarketLimitCounts() {
        long startedAt = System.nanoTime();
        String result = "success";
        try {
            MarketLimitCounts counts = ThsJsParser.parseMarketLimitCounts(thsIndexFlashClient.fetchIndexFlashRaw());
            if (counts == null) {
                result = "empty";
            }
            return counts;
        } catch (RuntimeException e) {
            result = metricResult(e);
            log.warn("同花顺涨跌停统计刷新失败,保留旧市场宽度缓存", e);
            return null;
        } finally {
            marketDataMetrics.record("ThsIndexFlashClient", "fetchIndexFlash", result, startedAt);
        }
    }

    private void refreshMoneyFlow() {
        long startedAt = System.nanoTime();
        String result = "success";
        try {
            String raw = push2Client.fetchNorthboundRaw();
            MoneyFlowSnapshot snapshot = EastmoneyJsParser.parseNorthbound(raw);
            if (snapshot != null) {
                moneyFlowCache = snapshot;
                persist();
            } else {
                result = "empty";
            }
        } catch (RuntimeException e) {
            result = metricResult(e);
            log.warn("北向资金刷新失败,保留旧缓存", e);
        } finally {
            marketDataMetrics.record("EastmoneyPush2Client", "fetchMoneyFlow", result, startedAt);
        }
    }

    private static String metricResult(RuntimeException exception) {
        if (exception instanceof feign.RetryableException) {
            return "timeout";
        }
        if (exception instanceof IllegalStateException) {
            return "parse_error";
        }
        return "failure";
    }

    /**
     * 刷新基金估值:遍历当前跟踪产品,由估值服务按源策略拉取。
     * <p>当日估值短时变化快,由后台 30s 周期刷新;读接口只读缓存,不等待外部接口。
     * 本轮失败、空响应或非当天数据会失效该基金旧缓存,防止旧估值冒充今日数据。
     * <p>本方法不触发指数、市场宽度、板块或资金请求,供境外市场扩展时段的定时任务复用。
     */
    public void refreshFundEstimates() {
        refreshFundEstimates(false);
    }

    public void refreshQdiiFundEstimates() {
        refreshFundEstimates(true);
    }

    private void refreshFundEstimates(boolean qdiiOnly) {
        if (!refreshingEstimates.compareAndSet(false, true)) {
            log.info("上一轮基金估值刷新尚未完成，跳过本轮");
            return;
        }
        int cursor = qdiiOnly ? qdiiEstimateCursor : allEstimateCursor;
        try {
            List<TrackedNavProductGateway.TrackedProduct> tracked = products.findAll().stream()
                    .filter(product -> !qdiiOnly
                            || product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.QDII)
                    .sorted(Comparator.comparingLong(TrackedNavProductGateway.TrackedProduct::fundProductId))
                    .toList();
            if (tracked.isEmpty()) {
                setEstimateCursor(qdiiOnly, 0);
                return;
            }
            Set<String> targetCodes = tracked.stream().filter(MarketRealtimeCache::supportsStandardNav)
                    .map(TrackedNavProductGateway.TrackedProduct::fundCode).collect(Collectors.toSet());
            int start = cursor < tracked.size() ? cursor : 0;
            int next = start;
            Instant deadline = clock.instant().plus(ESTIMATE_REFRESH_BUDGET);
            for (int index = start; index < tracked.size() && clock.instant().isBefore(deadline); index++) {
                try {
                    refreshFundEstimate(tracked.get(index), deadline, targetCodes);
                } catch (CancellationException exception) {
                    persist();
                    log.info("基金估值刷新达到 25 秒总期限，下轮从第 {} 只继续", index + 1);
                    setEstimateCursor(qdiiOnly, index);
                    return;
                }
                next = index + 1;
            }
            persist();
            if (next < tracked.size()) {
                log.info("基金估值刷新达到 25 秒总期限，下轮从第 {} 只继续", next + 1);
                setEstimateCursor(qdiiOnly, next);
                return;
            }
            setEstimateCursor(qdiiOnly, 0);
        } catch (RuntimeException e) {
            log.warn("基金估值刷新失败", e);
        } finally {
            refreshingEstimates.set(false);
        }
    }

    private void setEstimateCursor(boolean qdiiOnly, int cursor) {
        if (qdiiOnly) {
            qdiiEstimateCursor = cursor;
        } else {
            allEstimateCursor = cursor;
        }
    }

    private void refreshFundEstimate(TrackedNavProductGateway.TrackedProduct product, Instant deadline,
                                     Set<String> targetCodes) {
        String fundCode = product.fundCode();
        if (!supportsStandardNav(product)) {
            invalidateEstimate(fundCode, EstimateStatus.UNAVAILABLE);
            return;
        }
        if (isEstimateRetryCoolingDown(fundCode)) {
            return;
        }
        try {
            FundEstimateResult result = fundEstimateService.fetchEstimateResult(fundCode, deadline, targetCodes);
            FundEstimateSnapshot snapshot = result.snapshot();
            EstimateStatus status = classifyFreshness(result);
            if (status == EstimateStatus.AVAILABLE) {
                estimateCache.put(fundCode, snapshot);
                if (result.intradayChart() != null && result.intradayChart().points().size() >= 2) {
                    intradayCache.put(fundCode, result.intradayChart());
                } else {
                    intradayCache.remove(fundCode);
                }
                estimateStatuses.put(fundCode, EstimateStatus.AVAILABLE);
                estimateRetryAfter.remove(fundCode);
            } else {
                invalidateEstimate(fundCode, status);
                recordEstimateFailureBackoff(fundCode, status);
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException e) {
            invalidateEstimate(fundCode, EstimateStatus.PARSE_ERROR);
            recordEstimateFailureBackoff(fundCode, EstimateStatus.PARSE_ERROR);
            log.warn("基金 {} 估值刷新异常,已失效旧估值", fundCode, e);
        }
    }

    private static boolean supportsStandardNav(TrackedNavProductGateway.TrackedProduct product) {
        if (product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.MONEY_MARKET
                || product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.REIT) {
            return false;
        }
        String name = product.fundName();
        return name == null || (!name.contains("货币") && !name.toUpperCase(java.util.Locale.ROOT).contains("REIT")
                && !name.contains("不动产投资信托"));
    }

    private EstimateStatus classifyFreshness(FundEstimateResult result) {
        if (result.status() != EstimateStatus.AVAILABLE) {
            return result.status();
        }
        FundEstimateSnapshot snapshot = result.snapshot();
        if (snapshot == null || snapshot.estimatedChangePct() == null || snapshot.estimateTime() == null) {
            return EstimateStatus.UNAVAILABLE;
        }
        try {
            LocalDateTime estimateTime = LocalDateTime.parse(snapshot.estimateTime(), ESTIMATE_TIME_FORMATTER);
            boolean today = ChinaTradingDate.toUtcDate(estimateTime.atZone(ChinaTradingDate.ZONE).toInstant())
                    .equals(ChinaTradingDate.toUtcDate(clock.instant()));
            return today ? EstimateStatus.AVAILABLE : EstimateStatus.STALE;
        } catch (DateTimeParseException e) {
            return EstimateStatus.PARSE_ERROR;
        }
    }

    private void invalidateEstimate(String fundCode, EstimateStatus status) {
        if (fundCode == null) {
            return;
        }
        estimateCache.remove(fundCode);
        intradayCache.remove(fundCode);
        estimateStatuses.put(fundCode, status);
    }

    private boolean isEstimateRetryCoolingDown(String fundCode) {
        Instant retryAfter = estimateRetryAfter.get(fundCode);
        if (retryAfter == null || !retryAfter.isAfter(clock.instant())) {
            estimateRetryAfter.remove(fundCode);
            return false;
        }
        return true;
    }

    private void recordEstimateFailureBackoff(String fundCode, EstimateStatus status) {
        if (fundCode == null) {
            return;
        }
        if (status.isFailure()) {
            estimateRetryAfter.put(fundCode, clock.instant().plus(ESTIMATE_FAILURE_BACKOFF));
        } else {
            estimateRetryAfter.remove(fundCode);
        }
    }

    private void persist() {
        redisStore.save(new MarketRealtimeRedisStore.Snapshot(
                indexCache, breadthCache, sectorCache, moneyFlowCache,
                Map.copyOf(estimateCache), Map.copyOf(estimateStatuses), Map.copyOf(intradayCache)));
    }

}
