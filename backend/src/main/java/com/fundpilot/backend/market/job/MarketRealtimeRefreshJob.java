package com.fundpilot.backend.market.job;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 行情实时数据缓存刷新定时任务(行情工作台)。
 *
 * <p>A 股交易时段(9:30-11:30、13:00-15:00,Asia/Shanghai)每 30 秒刷新完整行情；
 * 其余主要市场覆盖窗口只刷新基金估值，以接住 QDII 晚间和跨自然日数据。
 *
 * <p>读接口只读内存缓存;盘中实时性由本任务保证。当前持仓基金数量较少,基金估值也按 30s 刷新,
 * 避免 /api/funds 与 /api/portfolio/summary 在盘中看到过旧估值。
 * JobMetricsAspect 自动给本任务加监控指标。
 */
@Component
@RequiredArgsConstructor
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
    private final Clock clock;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 交易时段每 30 秒触发一次,内部判断是否真的在交易时段。
     * cron 用 9-14 点放宽覆盖,精细时段(13:00-15:00)由 {@link #isTradingHours()} 二次过滤。
     */
    @Scheduled(cron = "*/30 * 9-14 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshRealtime() {
        if (!isTradingHours()) {
            return;
        }
        runOnce(cache::refreshAll);
    }

    /**
     * 覆盖 QDII 晚间和跨自然日估值窗口；A 股完整行情运行时由 refreshAll 一并刷新，避免重复请求。
     */
    @Scheduled(cron = "*/30 * 9-23 * * MON-FRI", zone = "Asia/Shanghai")
    @Scheduled(cron = "*/30 * 0-5 * * TUE-SAT", zone = "Asia/Shanghai")
    public void refreshFundEstimates() {
        if (isTradingHours()) {
            return;
        }
        // ponytail:市场归属未建模，先刷新全部普通基金；请求量成为瓶颈时再按市场拆分。
        runOnce(cache::refreshFundEstimates);
    }

    private void runOnce(Runnable refresh) {
        if (!refreshing.compareAndSet(false, true)) {
            log.info("上一轮行情缓存刷新尚未完成,跳过本轮");
            return;
        }
        try {
            refresh.run();
        } catch (RuntimeException e) {
            log.warn("行情缓存刷新周期异常: {}", e.getMessage());
        } finally {
            refreshing.set(false);
        }
    }

    /** 是否处于 A 股交易时段(9:30-11:30 或 13:00-15:00,上海时区),且当日为交易日。 */
    private boolean isTradingHours() {
        java.time.ZonedDateTime now = clock.instant().atZone(TRADING_ZONE);
        if (!isInSession(now.toLocalTime())) {
            return false;
        }
        // 当日非交易日在 trading_calendar 表中无记录或 tradingDay=false
        Instant todayUtc = ChinaTradingDate.toUtcDate(clock.instant());
        return tradingCalendarRepository.findByCalendarDate(todayUtc)
                .map(e -> e.isTradingDay())
                .orElse(false);
    }

    private boolean isInSession(LocalTime t) {
        return (t.isAfter(MORNING_OPEN) || t.equals(MORNING_OPEN)) && t.isBefore(MORNING_CLOSE)
                || (t.isAfter(AFTERNOON_OPEN) || t.equals(AFTERNOON_OPEN)) && t.isBefore(AFTERNOON_CLOSE);
    }
}
