package com.fundpilot.backend.market.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.common.RequiresNewTransactionExecutor;
import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundNavUpdatedEvent;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.entity.IndexKlineEntity;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.market.repository.IndexKlineRepository;
import com.fundpilot.backend.market.service.support.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 行情指标拉取编排服务(issue #7):每日 14:30/14:40/14:50 三批拉取所有未软删基金
 * 的当日市场指标,落 {@code market_indicator_snapshot}。
 * <p>分批策略:{@code Math.abs(fundId.hashCode()) % 3 == batchNumber} 切片,
 * 14:30 跑 batch 0、14:40 跑 batch 1、14:50 跑 batch 2。
 * <p>失败降级:单只基金 {@link MarketDataSource} 抛异常时记日志继续,不影响其他基金;
 * 该基金当天不写 snapshot,后续 {@code SignalGenerationJob} 读不到时出
 * {@code signalType=NONE, reason=INSUFFICIENT_MARKET_DATA}。
 */
@Service
@RequiredArgsConstructor
public class MarketDataFetchService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFetchService.class);
    private static final int TOTAL_BATCHES = 3;
    private static final String INDEX_KLINE_RANGE = "6"; // 6 = 近一年日 K

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final MarketDataSource marketDataSource;
    private final MarketIndicatorSnapshotService snapshotService;
    private final IndexKlineRepository indexKlineRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final RequiresNewTransactionExecutor requiresNewTransactionExecutor;

    /**
     * 拉取指定批次的基金行情指标。{@code batchNumber} 取 0/1/2,对应 14:30/14:40/14:50。
     */
    public void fetchBatch(int batchNumber) {
        // issue #23:范围从"有 EFFECTIVE 策略的基金"扩大到"所有未软删基金",
        // 让未建仓(观察池)基金也落净值历史支撑今日涨跌(story 21)。软删由 @SQLRestriction 自动过滤。
        List<Long> fundIds = fundRepository.findAll().stream()
                .map(FundEntity::getId)
                .toList();
        int success = 0;
        int failure = 0;
        for (Long fundId : fundIds) {
            if (Math.abs(fundId.hashCode()) % TOTAL_BATCHES != batchNumber) {
                continue;
            }
            try {
                requiresNewTransactionExecutor.execute(() -> {
                    fetchOne(fundId);
                    return null;
                });
                success++;
            } catch (RuntimeException ex) {
                failure++;
                log.warn("拉取基金 {} 行情指标失败,跳过当日 snapshot: {}", fundId, ex.getMessage());
            }
        }
        log.info("批次 {} 拉取完成:成功 {} 只,失败 {} 只", batchNumber, success, failure);
    }

    /**
     * 当日全量刷新——跑全部三批,供 {@code POST /api/admin/market-data/refresh} 手动触发。
     */
    public void refreshAll() {
        for (int batch = 0; batch < TOTAL_BATCHES; batch++) {
            fetchBatch(batch);
        }
    }

    /**
     * 拉取单只基金的历史净值落库(issue #37):供 {@code FundService.create} 建基金后自动拉取。
     * <p>由独立入口直接调用时开启事务；批处理路径通过
     * {@link RequiresNewTransactionExecutor} 为每只基金建立独立事务。
     *
     * @param fundId 基金 id
     */
    @org.springframework.transaction.annotation.Transactional
    public void fetchOneFund(Long fundId) {
        fetchOne(fundId);
    }

    private void fetchOne(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());

        List<FundNavSnapshot> navHistory = marketDataSource.fetchNavHistory(fund.getFundCode());
        if (navHistory == null || navHistory.isEmpty()) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY, "fund_code=" + fund.getFundCode() + " 净值历史为空");
        }
        List<BigDecimal> accumulatedNav = navHistory.stream()
                .map(FundNavSnapshot::accumulatedNav)
                .toList();

        upsertNavHistory(fund, navHistory);

        MarketIndicatorSnapshotEntity template = new MarketIndicatorSnapshotEntity();
        template.setFundEntity(fund);
        template.setSnapshotDate(today);
        FundNavSnapshot latest = navHistory.get(navHistory.size() - 1);
        template.setCurrentNav(latest.accumulatedNav());

        YearLineCalculator.calculate(accumulatedNav)
                .ifPresentOrElse(
                        m -> {
                            template.setPriceAboveYearLine(m.priceAboveYearLine());
                            template.setYearLineRising(m.yearLineRising());
                        },
                        () -> log.warn("fund_id={} 年线数据不足,priceAboveYearLine/yearLineRising 留默认", fundId));

        Optional<WeeklyMacdState> macd = WeeklyMacdCalculator.calculate(navHistory);
        macd.ifPresent(template::setWeeklyMacdState);

        SixtyDayHighCalculator.calculate(accumulatedNav)
                .ifPresent(template::setSixtyDayHigh);

        com.fundpilot.backend.fund.service.support.WeeklyDropCalculator.calculate(accumulatedNav)
                .ifPresent(template::setWeeklyDropPercent);

        if (fund.getBenchmarkIndexCode() != null && !fund.getBenchmarkIndexCode().isBlank()) {
            try {
                // benchmarkIndexCode 是 "000300.SH" 人类可读格式,转 secid "1.000300" 调东方财富接口
                String secid = SecidFormat.fromIndexCode(fund.getBenchmarkIndexCode())
                        .orElse(fund.getBenchmarkIndexCode());
                IndexKline kline = marketDataSource.fetchIndexKline(secid, INDEX_KLINE_RANGE);
                VolumeStateCalculator.calculate(kline).ifPresent(template::setVolumeState);
                // 顺便把已拉的日 K 落 index_kline 缓存(零额外请求),供 KlineService 渲染日/周/月 K,
                // 避免图表按需拉 push2his 触发 IP 限流。按 index_code+trade_date 去重只插缺失。
                upsertIndexKline(fund.getBenchmarkIndexCode(), kline);
            } catch (RuntimeException ex) {
                log.warn("fund_id={} 指数 K 线拉取失败,volumeState 留空: {}", fundId, ex.getMessage());
            }
        }

        snapshotService.upsert(template);
    }

    /**
     * 净值历史落库(issue #23):把 pingzhongdata 拉到的净值序列增量写入 fund_nav_history。
     * <p>按 fundId+navDate 去重——查已落库的 navDate 集合,只插不存在的,避免违反
     * uq_fund_nav_history_daily 部分唯一索引。首次全量落库,后续每日增量补最新一期。
     */
    private void upsertNavHistory(FundEntity fund, List<FundNavSnapshot> navHistory) {
        Set<Instant> existing = new HashSet<>(fundNavHistoryRepository.findNavDatesByFundEntity_Id(fund.getId()));
        List<FundNavHistoryEntity> toInsert = navHistory.stream()
                .filter(s -> !existing.contains(s.navDate()))
                .map(s -> {
                    FundNavHistoryEntity entity = new FundNavHistoryEntity();
                    entity.setFundEntity(fund);
                    entity.setNavDate(s.navDate());
                    entity.setNav(s.nav());
                    entity.setAccumulatedNav(s.accumulatedNav());
                    return entity;
                })
                .toList();
        if (!toInsert.isEmpty()) {
            fundNavHistoryRepository.saveAll(toInsert);
            eventPublisher.publishEvent(new FundNavUpdatedEvent(fund.getId()));
        }
    }

    /**
     * 指数日 K 落 index_kline 缓存:按 indexCode+tradeDate 去重,只插不存在的(对齐 upsertNavHistory 模式)。
     * <p>复用 {@link #fetchAndBuildSnapshot} 已拉的 {@link IndexKline}(算 VolumeState 用),零额外外部请求。
     * 缓存供 {@code KlineService} 渲染日/周/月 K,避免图表按需拉 push2his 触发 IP 限流。
     */
    private void upsertIndexKline(String indexCode, IndexKline kline) {
        if (indexCode == null || indexCode.isBlank()) return;
        Set<Instant> existing = new HashSet<>(indexKlineRepository.findTradeDatesByIndexCode(indexCode));
        List<IndexKlineEntity> toInsert = kline.bars().stream()
                .filter(b -> !existing.contains(b.date()))
                .map(b -> {
                    IndexKlineEntity entity = new IndexKlineEntity();
                    entity.setIndexCode(indexCode);
                    entity.setTradeDate(b.date());
                    entity.setOpen(b.open());
                    entity.setHigh(b.high());
                    entity.setLow(b.low());
                    entity.setClose(b.close());
                    entity.setVolume(b.volume());
                    return entity;
                })
                .toList();
        if (!toInsert.isEmpty()) {
            indexKlineRepository.saveAll(toInsert);
            log.debug("指数 K 线缓存写入 indexCode={} 新增 {} 条(已有 {} 条)", indexCode, toInsert.size(), existing.size());
        }
    }
}
