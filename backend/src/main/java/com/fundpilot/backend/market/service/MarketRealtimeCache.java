package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.EastmoneyJsParser;
import com.fundpilot.backend.market.client.EastmoneyPush2Client;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.IndexRealtimeSnapshot;
import com.fundpilot.backend.market.client.MoneyFlowSnapshot;
import com.fundpilot.backend.market.client.SectorSnapshot;
import com.fundpilot.backend.user.event.WatchedIndicesChangedEvent;
import com.fundpilot.backend.user.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 行情实时数据内存缓存(行情工作台核心)。
 *
 * <p>解决「前端 5-10s 高频轮询 vs 东方财富 2 req/s 限流」的矛盾:本服务以 30-60s 周期
 * 从东方财富拉数据填入 volatile 字段,前端轮询只读内存不触外部请求。
 *
 * <p>四类缓存:
 * <ul>
 *   <li>{@link #indexCache} 指数实时行情(用户关注列表,30s 刷新)</li>
 *   <li>{@link #sectorCache} 行业板块涨跌 + 主力资金(30s 刷新)</li>
 *   <li>{@link #moneyFlowCache} 北向资金(30s 刷新)</li>
 *   <li>{@link #estimateCache} 基金盘中估值(60s 刷新,N 只基金逐个拉)</li>
 * </ul>
 *
 * <p>降级策略:任一刷新失败保留旧缓存 + 记 warn(参考 {@link FundEstimateService} 的 catch RuntimeException 模式),
 * 不抛异常中断整个刷新周期——前端继续看到旧数据优于无数据。
 */
@Service
@RequiredArgsConstructor
public class MarketRealtimeCache {

    private static final Logger log = LoggerFactory.getLogger(MarketRealtimeCache.class);

    private final EastmoneyPush2Client push2Client;
    private final FundEstimateService fundEstimateService;
    private final UserConfigService userConfigService;
    private final FundRepository fundRepository;

    // volatile 保证可见性;定时刷新单线程写,前端读线程只读,无需加锁
    private volatile List<IndexRealtimeSnapshot> indexCache = List.of();
    private volatile List<SectorSnapshot> sectorCache = List.of();
    private volatile MoneyFlowSnapshot moneyFlowCache = null;
    private final Map<String, FundEstimateSnapshot> estimateCache = new ConcurrentHashMap<>();

    /** 读指数缓存(已按用户关注列表过滤,顺序按请求 secid 顺序)。 */
    public List<IndexRealtimeSnapshot> getIndices() {
        return indexCache;
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
     * 批量读基金估值(缺失的实时拉取,受 2 req/s 限流分批进行)。
     * @param fundCodes 基金代码列表
     * @return code → 估值快照;拉取失败的 code 不出现在 map 中
     */
    public Map<String, FundEstimateSnapshot> getEstimates(List<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Map.of();
        }
        return fundCodes.stream()
                .filter(estimateCache::containsKey)
                .collect(Collectors.toMap(Function.identity(), estimateCache::get));
    }

    /**
     * 全量刷新四类缓存——由 {@code MarketRealtimeRefreshJob} 在交易时段定时调用。
     * 任一类失败不影响其他类(独立 try-catch)。加 @Transactional(readOnly=true)
     * 以复用 JPA 上下文读 FundRepository(行情展示需要拉基金列表算估值)。
     */
    @Transactional(readOnly = true)
    public void refreshAll() {
        refreshIndices();
        refreshSectors();
        refreshMoneyFlow();
        refreshFundEstimates();
    }

    /**
     * 仅刷新指数/板块/资金三类(不含基金估值)——估值刷新慢(N 只 × 2 req/s),
     * 由 Job 每 60s 调一次全量,中间 30s 周期只刷快数据,避免争用限流桶。
     */
    @Transactional(readOnly = true)
    public void refreshRealtimeWithoutEstimates() {
        refreshIndices();
        refreshSectors();
        refreshMoneyFlow();
    }

    /**
     * 用户关注指数变更时即时刷新指数缓存(由 UserConfigService.update 发的事件触发)。
     * <p>不受交易时段限制——用户改了关注列表就是想立刻看,即便盘后也应展示最新选中的指数行情
     * (东方财富盘后仍可返回收盘数据)。仅刷新指数,板块/资金/估值由各自周期维护。
     */
    @EventListener
    @Transactional(readOnly = true)
    public void onWatchedIndicesChanged(@SuppressWarnings("unused") WatchedIndicesChangedEvent event) {
        refreshIndices();
    }

    private void refreshIndices() {
        try {
            List<String> secids = userConfigService.getWatchedIndices();
            if (secids.isEmpty()) {
                indexCache = List.of();
                return;
            }
            String raw = push2Client.fetchIndexRealtimeRaw(String.join(",", secids));
            indexCache = EastmoneyJsParser.parseIndexRealtime(raw, secids);
        } catch (RuntimeException e) {
            log.warn("指数实时行情刷新失败,保留旧缓存: {}", e.getMessage());
        }
    }

    private void refreshSectors() {
        try {
            String raw = push2Client.fetchSectorListRaw("f3");
            sectorCache = EastmoneyJsParser.parseSectorList(raw);
        } catch (RuntimeException e) {
            log.warn("行业板块刷新失败,保留旧缓存: {}", e.getMessage());
        }
    }

    private void refreshMoneyFlow() {
        try {
            String raw = push2Client.fetchNorthboundRaw();
            MoneyFlowSnapshot snapshot = EastmoneyJsParser.parseNorthbound(raw);
            if (snapshot != null) {
                moneyFlowCache = snapshot;
            }
        } catch (RuntimeException e) {
            log.warn("北向资金刷新失败,保留旧缓存: {}", e.getMessage());
        }
    }

    /**
     * 刷新基金估值:遍历所有非清仓基金,逐个拉 fundgz,失败降级跳过。
     * <p>N 只基金 × 2 req/s 限流 = N/2 秒一轮,故刷新周期设 60s(指数/板块/资金 30s)。
     * 估值是盘中短时态数据,60s 滞后可接受(前端 5-10s 轮询时最多看 60s 前的估值)。
     */
    private void refreshFundEstimates() {
        try {
            List<FundEntity> funds = fundRepository.findByStatus(FundStatus.HOLDING);
            for (FundEntity fund : funds) {
                try {
                    fundEstimateService.fetchEstimate(fund.getFundCode())
                            .ifPresent(s -> estimateCache.put(fund.getFundCode(), s));
                } catch (RuntimeException e) {
                    // 单只失败不影响其他,跳过这只本轮
                }
            }
        } catch (RuntimeException e) {
            log.warn("基金估值刷新失败: {}", e.getMessage());
        }
    }
}
