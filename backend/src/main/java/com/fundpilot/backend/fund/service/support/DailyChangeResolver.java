package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 三态今日涨跌判定纯函数(issue #38,PRD #34 / ADR-0008)。
 *
 * <p>「今日涨跌」是单一概念,值随时段切换数据源:
 * <ul>
 *   <li><b>盘前</b>(北京时间 &lt; 9:30)= 0,isEstimated=false(还没开盘,今天没变化)</li>
 *   <li><b>盘中/待公布</b>(≥9:30 且当日净值未落库)= fundgz gszzl,isEstimated=true;
 *       无 fundgz 估值时降级用落库最近两期净值算(T-1 vs T-2),isEstimated=false</li>
 *   <li><b>盘后</b>(当日净值已落库)= 当日累计净值 / 昨日累计净值 - 1,isEstimated=false</li>
 * </ul>
 *
 * <p>判定优先级:当日净值已落库(盘后)→ 用落库净值算;否则按北京时间判盘前(0)/盘中(估值或降级)。
 * 15:00-20:00 待公布时段也走"盘中/待公布"分支(当日净值未落库),显示 fundgz 最后一次估值。
 *
 * <p>纯函数,无 Spring/DB 依赖,外部值(当前时间、净值是否落库、落库净值、fundgz 估值)由调用方注入。
 */
public final class DailyChangeResolver {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** A 股交易时区(北京时间)。 */
    private static final ZoneOffset BEIJING = ZoneOffset.ofHours(8);
    /** 开盘时间 9:30(北京时间),盘前 < 此值今日涨跌为 0。 */
    private static final int MARKET_OPEN_MINUTES = 9 * 60 + 30;

    private DailyChangeResolver() {
    }

    /**
     * @param now               当前时间
     * @param todayNavConfirmed 当日净值是否已落库(盘后态判定)
     * @param latestNav         落库的最近一期累计净值(盘后态 / 降级态用)
     * @param previousNav       落库的上一期累计净值(同上)
     * @param estimate          fundgz 盘中估值(盘中态用,空则降级)
     * @return 今日涨跌幅 + 是否估算
     */
    public static DailyChangeResult resolve(Instant now, boolean todayNavConfirmed,
                                            BigDecimal latestNav, BigDecimal previousNav,
                                            Optional<FundEstimateSnapshot> estimate) {
        // 盘后态:当日净值已落库 → 用落库净值算(当日/昨日-1),非估算
        if (todayNavConfirmed) {
            return new DailyChangeResult(FundPnlCalculator.dailyChangePct(latestNav, previousNav), false);
        }
        // 盘前态:北京时间 < 9:30 → 0,非估算
        if (isBeforeMarketOpen(now)) {
            return new DailyChangeResult(BigDecimal.ZERO, false);
        }
        // 盘中/待公布态:≥9:30 且当日净值未落库
        // 有 fundgz 估值 → 用 gszzl,标记估算
        if (estimate.isPresent() && estimate.get().estimatedChangePct() != null) {
            return new DailyChangeResult(estimate.get().estimatedChangePct(), true);
        }
        // 无 fundgz 估值 → 降级用落库最近两期净值算(T-1 vs T-2),非估算
        return new DailyChangeResult(FundPnlCalculator.dailyChangePct(latestNav, previousNav), false);
    }

    /** 当前时间是否在 A 股开盘前(北京时间 < 9:30)。 */
    private static boolean isBeforeMarketOpen(Instant now) {
        ZonedDateTime beijing = now.atZone(BEIJING);
        int minutesOfDay = beijing.getHour() * 60 + beijing.getMinute();
        return minutesOfDay < MARKET_OPEN_MINUTES;
    }
}
