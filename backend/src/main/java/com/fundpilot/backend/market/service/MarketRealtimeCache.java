package com.fundpilot.backend.market.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.EastmoneyJsParser;
import com.fundpilot.backend.market.client.EastmoneyPush2Client;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.FundIntradayChart;
import com.fundpilot.backend.market.client.IndexRealtimeSnapshot;
import com.fundpilot.backend.market.client.MarketBreadthSnapshot;
import com.fundpilot.backend.market.client.MoneyFlowSnapshot;
import com.fundpilot.backend.market.client.SectorSnapshot;
import com.fundpilot.backend.user.event.WatchedIndicesChangedEvent;
import com.fundpilot.backend.user.service.UserConfigService;
import com.fundpilot.backend.market.service.support.FundMarketDataCapability;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final List<String> MARKET_BREADTH_SECIDS = List.of(
            "1.000001", // 上证指数：沪市宽度
            "0.399001", // 深证成指：深市宽度
            "0.899050"  // 北证 50：北交所宽度
    );

    private final EastmoneyPush2Client push2Client;
    private final FundEstimateService fundEstimateService;
    private final UserConfigService userConfigService;
    private final FundRepository fundRepository;
    private final MarketDataMetrics marketDataMetrics;
    private final Clock clock;
    private final MarketRealtimeRedisStore redisStore;

    // volatile 保证可见性;定时刷新单线程写,前端读线程只读,无需加锁
    private volatile List<IndexRealtimeSnapshot> indexCache = List.of();
    private volatile MarketBreadthSnapshot breadthCache = null;
    private volatile List<SectorSnapshot> sectorCache = List.of();
    private volatile MoneyFlowSnapshot moneyFlowCache = null;
    private final Map<String, FundEstimateSnapshot> estimateCache = new ConcurrentHashMap<>();
    private final Map<String, FundIntradayChart> intradayCache = new ConcurrentHashMap<>();
    private final Map<String, EstimateStatus> estimateStatuses = new ConcurrentHashMap<>();
    private final Map<String, Instant> estimateRetryAfter = new ConcurrentHashMap<>();

    @PostConstruct
    void restoreFromRedis() {
        redisStore.load().ifPresent(snapshot -> {
            indexCache = snapshot.indices() == null ? List.of() : List.copyOf(snapshot.indices());
            breadthCache = snapshot.breadth();
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

    /** 读取基金详情的当日分时缓存；请求链路不访问外部行情源。 */
    public FundIntradayChart getIntraday(Long fundId) {
        if (fundId == null) {
            return null;
        }
        return fundRepository.findById(fundId)
                .map(FundEntity::getFundCode)
                .map(intradayCache::get)
                .orElse(null);
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
     * 全量刷新五类缓存——由 {@code MarketRealtimeRefreshJob} 在交易时段定时调用。
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
     * 用户关注指数变更时即时刷新指数与市场宽度缓存(由 UserConfigService.update 发的事件触发)。
     * <p>不受交易时段限制——用户改了关注列表就是想立刻看,即便盘后也应展示最新选中的指数行情
     * (东方财富盘后仍可返回收盘数据)。仅刷新指数,板块/资金/估值由各自周期维护。
     */
    @EventListener
    public void onWatchedIndicesChanged(@SuppressWarnings("unused") WatchedIndicesChangedEvent event) {
        refreshIndices();
    }

    /**
     * 应用启动时预热指数/板块/资金缓存,不在启动线程逐只刷新基金估值。
     *
     * <p>修复 bug:定时 Job({@link com.fundpilot.backend.market.job.MarketRealtimeRefreshJob})
     * 仅交易时段(MON-FRI 9:30-15:00)跑,部署发生在非交易时段(周末/盘后/盘前)时
     * {@code indexCache} 初始空,工作台显示「暂无关注指数」直到用户重新配置触发
     * {@link WatchedIndicesChangedEvent}。启动时刷一次,盘后/周末也能展示收盘数据
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
            log.warn("行情缓存启动刷新失败,前端将显示空态直到下次定时刷新: {}", e.getMessage());
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

    private void refreshIndices() {
        long startedAt = System.nanoTime();
        String result = "success";
        try {
            List<String> watchedSecids = userConfigService.getWatchedIndices();
            Set<String> requestedSecids = new LinkedHashSet<>(watchedSecids);
            requestedSecids.addAll(MARKET_BREADTH_SECIDS);

            List<String> requestOrder = List.copyOf(requestedSecids);
            String raw = push2Client.fetchIndexRealtimeRaw(String.join(",", requestOrder));
            Map<String, IndexRealtimeSnapshot> snapshotsBySecid = EastmoneyJsParser
                    .parseIndexRealtime(raw, requestOrder).stream()
                    .collect(Collectors.toMap(IndexRealtimeSnapshot::secid, Function.identity()));
            indexCache = watchedSecids.stream()
                    .map(snapshotsBySecid::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            MarketBreadthSnapshot breadth = EastmoneyJsParser.parseMarketBreadth(raw, MARKET_BREADTH_SECIDS);
            if (breadth != null) {
                breadthCache = breadth;
            }
            if (snapshotsBySecid.isEmpty() && breadth == null) {
                result = "empty";
            }
            persist();
        } catch (RuntimeException e) {
            result = metricResult(e);
            log.warn("指数实时行情与市场宽度刷新失败,保留旧缓存: {}", e.getMessage());
        } finally {
            marketDataMetrics.record("EastmoneyPush2Client", "fetchIndices", result, startedAt);
        }
    }

    private void refreshSectors() {
        long startedAt = System.nanoTime();
        String result = "success";
        try {
            String raw = push2Client.fetchSectorListRaw("f3");
            sectorCache = EastmoneyJsParser.parseSectorList(raw);
            if (sectorCache.isEmpty()) {
                result = "empty";
            }
            persist();
        } catch (RuntimeException e) {
            result = metricResult(e);
            log.warn("行业板块刷新失败,保留旧缓存: {}", e.getMessage());
        } finally {
            marketDataMetrics.record("EastmoneyPush2Client", "fetchSectors", result, startedAt);
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
            log.warn("北向资金刷新失败,保留旧缓存: {}", e.getMessage());
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
     * 刷新基金估值:遍历所有未软删基金(含观察池),逐个拉 fundgz。
     * <p>当日估值短时变化快,由后台 30s 周期刷新;读接口只读缓存,不等待外部接口。
     * 本轮失败、空响应或非当天数据会失效该基金旧缓存,防止旧估值冒充今日数据。
     * <p>本方法不触发指数、市场宽度、板块或资金请求,供境外市场扩展时段的定时任务复用。
     */
    public void refreshFundEstimates() {
        try {
            List<FundEntity> funds = fundRepository.findAll();
            for (FundEntity fund : funds) {
                String fundCode = fund.getFundCode();
                if (!FundMarketDataCapability.supportsStandardNav(fund)) {
                    invalidateEstimate(fundCode, EstimateStatus.UNAVAILABLE);
                    continue;
                }
                if (isEstimateRetryCoolingDown(fundCode)) {
                    continue;
                }
                try {
                    FundEstimateResult result = fundEstimateService.fetchEstimateResult(fundCode);
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
                } catch (RuntimeException e) {
                    invalidateEstimate(fundCode, EstimateStatus.PARSE_ERROR);
                    recordEstimateFailureBackoff(fundCode, EstimateStatus.PARSE_ERROR);
                    log.warn("基金 {} 估值刷新异常,已失效旧估值: {}", fundCode, e.getMessage());
                }
            }
            persist();
        } catch (RuntimeException e) {
            log.warn("基金估值刷新失败: {}", e.getMessage());
        }
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
