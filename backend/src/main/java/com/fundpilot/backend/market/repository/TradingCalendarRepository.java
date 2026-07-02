package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TradingCalendarRepository extends JpaRepository<TradingCalendarEntity, Long> {

    Optional<TradingCalendarEntity> findByCalendarDate(Instant calendarDate);

    /**
     * 统计 (fromExclusive, toInclusive] 区间(不含 from、含 to)的交易日数,
     * 供 MIN_HOLD_DAYS 判定"买入后经过的交易日数"。calendarDate 经 InstantDateConverter 存为 DATE。
     */
    @Query("select count(e) from TradingCalendarEntity e " +
            "where e.calendarDate > :fromExclusive and e.calendarDate <= :toInclusive and e.tradingDay = true")
    long countTradingDaysBetween(@Param("fromExclusive") Instant fromExclusive,
                                 @Param("toInclusive") Instant toInclusive);

    /**
     * 判断 {@code date} 是否为当月最后一个交易日:今日是交易日 且 当月(date 所在自然月)内
     * 没有比 date 更晚的交易日。用 SQL 的 date_trunc('month',...) 比对自然月,避免 Java LocalDate。
     */
    @Query(value = "select exists(select 1 from trading_calendar t " +
            "where t.deleted_date is null and t.is_trading_day = true " +
            "and t.calendar_date = cast(:date as date) " +
            "and not exists (select 1 from trading_calendar t2 " +
            "where t2.deleted_date is null and t2.is_trading_day = true " +
            "and t2.calendar_date > t.calendar_date " +
            "and date_trunc('month', t2.calendar_date) = date_trunc('month', t.calendar_date)))",
            nativeQuery = true)
    boolean isLastTradingDayOfMonth(@Param("date") Instant date);
}
