package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 行情实时数据缓存刷新定时任务(行情工作台)。
 *
 * <p>交易时段(A 股 9:30-11:30、13:00-15:00,Asia/Shanghai 时区)每 30 秒刷新一次缓存,
 * 非交易时段(含周末/节假日/盘前/盘后)休眠不刷新,前端继续读最后一次缓存数据。
 *
 * <p>_refreshIndex 与 _refreshFundEstimates 错峰执行:指数/板块/资金每 30s,
 * 基金估值因 N 只 × 2 req/s 限流单独 60s 一轮。
 * JobMetricsAspect 自动给本任务加监控指标。
 */
@Component
public class MarketRealtimeRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MarketRealtimeRefreshJob.class);
    /** A 股交易时区(上海),所有交易时段判断基于此时区。 */
    private static final ZoneId TRADING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private final MarketRealtimeCache cache;
    private final TradingCalendarRepository tradingCalendarRepository;
    /** 估值刷新节流:两次刷新之间至少间隔一个完整周期(60s),避免与指数刷新争用限流桶。 */
    private volatile long lastEstimateRefreshEpoch = 0;

    public MarketRealtimeRefreshJob(MarketRealtimeCache cache,
                                    TradingCalendarRepository tradingCalendarRepository) {
        this.cache = cache;
        this.tradingCalendarRepository = tradingCalendarRepository;
    }

    /**
     * 交易时段每 30 秒触发一次,内部判断是否真的在交易时段。
     * cron 用 9-14 点放宽覆盖,精细时段(13:00-15:00)由 {@link #isTradingHours()} 二次过滤。
     */
    @Scheduled(cron = "*/30 * 9-14 * * MON-FRI")
    public void refreshRealtime() {
        if (!isTradingHours()) {
            return;
        }
        try {
            // 估值每两轮刷一次(60s),指数/板块/资金每轮刷(30s)
            long now = System.currentTimeMillis();
            boolean refreshEstimates = (now - lastEstimateRefreshEpoch) >= 60_000L;
            if (refreshEstimates) {
                cache.refreshAll();
                lastEstimateRefreshEpoch = now;
            } else {
                cache.refreshRealtimeWithoutEstimates();
            }
        } catch (RuntimeException e) {
            log.warn("行情缓存刷新周期异常: {}", e.getMessage());
        }
    }

    /** 是否处于 A 股交易时段(9:30-11:30 或 13:00-15:00,上海时区),且当日为交易日。 */
    private boolean isTradingHours() {
        java.time.ZonedDateTime now = Instant.now().atZone(TRADING_ZONE);
        // 当日非交易日在 trading_calendar 表中无记录或 tradingDay=false
        Instant todayUtc = now.toLocalDate().atStartOfDay(TRADING_ZONE).toInstant();
        return tradingCalendarRepository.findByCalendarDate(todayUtc)
                .map(e -> e.isTradingDay())
                .orElse(false)
                && isInSession(now.toLocalTime());
    }

    private boolean isInSession(LocalTime t) {
        return (t.isAfter(MORNING_OPEN) || t.equals(MORNING_OPEN)) && t.isBefore(MORNING_CLOSE)
                || (t.isAfter(AFTERNOON_OPEN) || t.equals(AFTERNOON_OPEN)) && t.isBefore(AFTERNOON_CLOSE);
    }
}
