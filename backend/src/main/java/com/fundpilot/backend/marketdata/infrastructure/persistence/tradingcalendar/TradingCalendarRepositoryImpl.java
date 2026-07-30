package com.fundpilot.backend.marketdata.infrastructure.persistence.tradingcalendar;

import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingCalendarRepository;
import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingDay;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class TradingCalendarRepositoryImpl implements TradingCalendarRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean isTradingDay(Instant date) {
        return Boolean.TRUE.equals(jdbc.query("""
                SELECT is_trading_day FROM trading_calendar
                WHERE calendar_date = ? AND deleted_date IS NULL
                """, rs -> rs.next() ? rs.getBoolean(1) : false, sqlDate(date)));
    }
    @Override public Optional<Instant> latestOnOrBefore(Instant date) {
        return optionalDate("calendar_date <= ?", date);
    }
    @Override public Optional<Instant> latestBefore(Instant date) {
        return optionalDate("calendar_date < ?", date);
    }
    @Override public Optional<Instant> maxDate() {
        LocalDate value = jdbc.query("SELECT max(calendar_date) FROM trading_calendar WHERE deleted_date IS NULL",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null);
        return Optional.ofNullable(value).map(TradingCalendarRepositoryImpl::instant);
    }
    @Override public List<Instant> tradingDaysBetween(Instant startInclusive, Instant endExclusive) {
        return jdbc.query("""
                SELECT calendar_date FROM trading_calendar
                WHERE calendar_date >= ? AND calendar_date < ? AND is_trading_day = true AND deleted_date IS NULL
                ORDER BY calendar_date
                """, (rs, row) -> instant(rs.getObject(1, LocalDate.class)), sqlDate(startInclusive),
                sqlDate(endExclusive));
    }
    @Override public long countBetween(Instant fromExclusive, Instant toInclusive) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM trading_calendar
                WHERE calendar_date > ? AND calendar_date <= ? AND is_trading_day = true
                  AND deleted_date IS NULL
                """, Long.class, sqlDate(fromExclusive), sqlDate(toInclusive));
        return count == null ? 0 : count;
    }
    @Override public int addIfAbsent(List<TradingDay> tradingDays) {
        int added = 0;
        for (TradingDay day : tradingDays) {
            added += jdbc.update("""
                    INSERT INTO trading_calendar
                        (calendar_date, is_trading_day, version, created_date, updated_date)
                    VALUES (?, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (calendar_date) WHERE deleted_date IS NULL DO NOTHING
                    """, sqlDate(day.calendarDate()));
        }
        return added;
    }

    private Optional<Instant> optionalDate(String predicate, Instant date) {
        List<LocalDate> values = jdbc.query("""
                SELECT calendar_date FROM trading_calendar
                WHERE %s AND is_trading_day = true AND deleted_date IS NULL
                ORDER BY calendar_date DESC LIMIT 1
                """.formatted(predicate), (rs, row) -> rs.getObject(1, LocalDate.class), sqlDate(date));
        return values.stream().findFirst().map(TradingCalendarRepositoryImpl::instant);
    }
    private static Date sqlDate(Instant value) { return Date.valueOf(value.atZone(ZoneOffset.UTC).toLocalDate()); }
    private static Instant instant(LocalDate value) { return value.atStartOfDay(ZoneOffset.UTC).toInstant(); }
}
