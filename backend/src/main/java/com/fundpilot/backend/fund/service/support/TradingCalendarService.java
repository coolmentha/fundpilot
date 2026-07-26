package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 交易日历服务:判定某日是否为 A 股交易日,及两个日期间(不含起点、含终点)的交易日数。
 * <p>卖出纪律的 MIN_HOLD_DAYS(5 个交易日窗口)判定时调
 * {@link #daysBetweenTradingDays},起算点取每次买入 confirmTime 的最大值
 * (CONTEXT.md「7 天内不赎回硬约束」)。日期缺失保守返 false(不误判为交易日)。
 * <p>入参统一用 {@link Instant}(UTC 0 点表当日),经 {@code InstantDateConverter} 转 DATE 查库。
 */
@Service
@RequiredArgsConstructor
public class TradingCalendarService {

    private final TradingCalendarApi tradingCalendarApi;

    public boolean isTradingDay(Instant date) {
        return tradingCalendarApi.isTradingDay(date);
    }

    /** 查给定日期之前最近一个 A 股交易日，周末会返回周五。 */
    public Optional<Instant> latestTradingDayOnOrBefore(Instant date) {
        return tradingCalendarApi.latestOnOrBefore(date);
    }

    /** 查给定日期之前最近一个 A 股交易日，不包含给定日期。 */
    public Optional<Instant> latestTradingDayBefore(Instant date) {
        return tradingCalendarApi.latestBefore(date);
    }

    /**
     * @param fromExclusive 起点日期(不含,UTC 0 点)
     * @param toInclusive   终点日期(含,UTC 0 点)
     * @return (from, to] 区间内的交易日数
     */
    public long daysBetweenTradingDays(Instant fromExclusive, Instant toInclusive) {
        return tradingCalendarApi.countBetween(fromExclusive, toInclusive);
    }
}
