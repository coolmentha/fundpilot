package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TradingCalendarRepository extends JpaRepository<TradingCalendarEntity, Long> {

    Optional<TradingCalendarEntity> findByCalendarDate(Instant calendarDate);

    /** 当前未软删日历的最大日期,供日常同步只追加后续日期。 */
    @Query("select max(e.calendarDate) from TradingCalendarEntity e")
    Optional<Instant> findMaxCalendarDate();

    @Modifying
    @Query(value = """
            INSERT INTO trading_calendar
                (calendar_date, is_trading_day, version, created_date, updated_date)
            VALUES
                (CAST(CAST(:calendarDate AS TIMESTAMPTZ) AT TIME ZONE 'UTC' AS DATE),
                 TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (calendar_date) WHERE deleted_date IS NULL DO NOTHING
            """, nativeQuery = true)
    int insertTradingDayIfAbsent(@Param("calendarDate") Instant calendarDate);

    /**
     * 统计 (fromExclusive, toInclusive] 区间(不含 from、含 to)的交易日数,
     * 供 MIN_HOLD_DAYS 判定"买入后经过的交易日数"。calendarDate 经 InstantDateConverter 存为 DATE。
     */
    @Query("select count(e) from TradingCalendarEntity e " +
            "where e.calendarDate > :fromExclusive and e.calendarDate <= :toInclusive and e.tradingDay = true")
    long countTradingDaysBetween(@Param("fromExclusive") Instant fromExclusive,
                                 @Param("toInclusive") Instant toInclusive);
}
