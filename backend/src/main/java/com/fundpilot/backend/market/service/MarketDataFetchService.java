package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.EastmoneyClient;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.market.service.support.SixtyDayHighCalculator;
import com.fundpilot.backend.market.service.support.VolumeStateCalculator;
import com.fundpilot.backend.market.service.support.WeeklyMacdCalculator;
import com.fundpilot.backend.market.service.support.YearLineCalculator;
import com.fundpilot.backend.market.service.support.YearLineMetrics;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 行情指标拉取编排服务(issue #7):每日 14:30/14:40/14:50 三批拉取所有 EFFECTIVE 策略基金
 * 的当日市场指标,落 {@code market_indicator_snapshot}。
 * <p>分批策略:{@code Math.abs(fundId.hashCode()) % 3 == batchNumber} 切片,
 * 14:30 跑 batch 0、14:40 跑 batch 1、14:50 跑 batch 2。
 * <p>失败降级:单只基金 {@link EastmoneyClient} 抛异常时记日志继续,不影响其他基金;
 * 该基金当天不写 snapshot,后续 {@code SignalGenerationJob} 读不到时出
 * {@code signalType=NONE, reason=INSUFFICIENT_MARKET_DATA}。
 */
@Service
public class MarketDataFetchService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFetchService.class);
    private static final int TOTAL_BATCHES = 3;
    private static final String INDEX_KLINE_RANGE = "6"; // 6 = 近一年日 K

    private final FundStrategyRepository fundStrategyRepository;
    private final FundRepository fundRepository;
    private final EastmoneyClient eastmoneyClient;
    private final MarketIndicatorSnapshotService snapshotService;

    public MarketDataFetchService(FundStrategyRepository fundStrategyRepository,
                                  FundRepository fundRepository,
                                  EastmoneyClient eastmoneyClient,
                                  MarketIndicatorSnapshotService snapshotService) {
        this.fundStrategyRepository = fundStrategyRepository;
        this.fundRepository = fundRepository;
        this.eastmoneyClient = eastmoneyClient;
        this.snapshotService = snapshotService;
    }

    /**
     * 拉取指定批次的基金行情指标。{@code batchNumber} 取 0/1/2,对应 14:30/14:40/14:50。
     */
    @Transactional
    public void fetchBatch(int batchNumber) {
        List<Long> effectiveFundIds = fundStrategyRepository.findEffectiveFundIds();
        int success = 0;
        int failure = 0;
        for (Long fundId : effectiveFundIds) {
            if (Math.abs(fundId.hashCode()) % TOTAL_BATCHES != batchNumber) {
                continue;
            }
            try {
                fetchOne(fundId);
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
    @Transactional
    public void refreshAll() {
        for (int batch = 0; batch < TOTAL_BATCHES; batch++) {
            fetchBatch(batch);
        }
    }

    private void fetchOne(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new IllegalStateException("fund_id=" + fundId + " 不存在"));
        LocalDate today = LocalDate.now();

        List<FundNavSnapshot> navHistory = eastmoneyClient.fetchNavHistory(fund.getFundCode());
        if (navHistory == null || navHistory.isEmpty()) {
            throw new IllegalStateException("fund_code=" + fund.getFundCode() + " 净值历史为空");
        }
        List<BigDecimal> accumulatedNav = navHistory.stream()
                .map(FundNavSnapshot::accumulatedNav)
                .toList();

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
                IndexKline kline = eastmoneyClient.fetchIndexKline(fund.getBenchmarkIndexCode(), INDEX_KLINE_RANGE);
                VolumeStateCalculator.calculate(kline).ifPresent(template::setVolumeState);
            } catch (RuntimeException ex) {
                log.warn("fund_id={} 指数 K 线拉取失败,volumeState 留空: {}", fundId, ex.getMessage());
            }
        }

        snapshotService.upsert(template);
    }
}
