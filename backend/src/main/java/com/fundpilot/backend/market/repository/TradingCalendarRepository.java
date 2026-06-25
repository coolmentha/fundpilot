package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TradingCalendarRepository extends JpaRepository<TradingCalendarEntity, Long> {

    Optional<TradingCalendarEntity> findByCalendarDate(LocalDate calendarDate);

    /**
     * 统计 (fromExclusive, toInclusive] 区间(不含 from、含 to)的交易日数,
     * 供 MIN_HOLD_DAYS 判定"买入后经过的交易日数"。
     */
    @Query("select count(e) from TradingCalendarEntity e " +
            "where e.calendarDate > :fromExclusive and e.calendarDate <= :toInclusive and e.tradingDay = true")
    long countTradingDaysBetween(@Param("fromExclusive") LocalDate fromExclusive,
                                 @Param("toInclusive") LocalDate toInclusive);
}
