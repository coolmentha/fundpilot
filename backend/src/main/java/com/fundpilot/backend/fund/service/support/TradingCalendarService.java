package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 交易日历服务:判定某日是否为 A 股交易日,及两个日期间(不含起点、含终点)的交易日数。
 * <p>{@link HardConstraintConfig#MIN_HOLD_DAYS} 判定 5 个交易日窗口时调
 * {@link #daysBetweenTradingDays},起算点取每次买入 confirmTime 的最大值
 * (CONTEXT.md「7 天内不赎回硬约束」)。日期缺失保守返 false(不误判为交易日)。
 */
@Service
public class TradingCalendarService {

    private final TradingCalendarRepository tradingCalendarRepository;

    public TradingCalendarService(TradingCalendarRepository tradingCalendarRepository) {
        this.tradingCalendarRepository = tradingCalendarRepository;
    }

    public boolean isTradingDay(LocalDate date) {
        return tradingCalendarRepository.findByCalendarDate(date)
                .map(TradingCalendarEntity::isTradingDay)
                .orElse(false);
    }

    /**
     * @param fromExclusive 起点日期(不含)
     * @param toInclusive   终点日期(含)
     * @return (from, to] 区间内的交易日数
     */
    public long daysBetweenTradingDays(LocalDate fromExclusive, LocalDate toInclusive) {
        return tradingCalendarRepository.countTradingDaysBetween(fromExclusive, toInclusive);
    }
}
