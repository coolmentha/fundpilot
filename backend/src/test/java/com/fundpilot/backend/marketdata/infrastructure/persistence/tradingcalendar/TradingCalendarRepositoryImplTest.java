package com.fundpilot.backend.marketdata.infrastructure.persistence.tradingcalendar;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 交易日历查询的日期口径:输入时刻必须按北京时间落业务日,否则前端传 "09-15T00:00:00+08:00"
 * (UTC 09-14 16:00)会被截成 09-14,导致"选 15 号生成 14 号流水"。
 */
class TradingCalendarRepositoryImplTest {

    private static final Instant BEIJING_MIDNIGHT = Instant.parse("2026-09-14T16:00:00Z"); // 北京 09-15 00:00
    private static final Date BEIJING_MIDNIGHT_DATE = java.sql.Date.valueOf("2026-09-15");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TradingCalendarRepositoryImpl repository = new TradingCalendarRepositoryImpl(jdbc);

    @Test
    void latestOnOrBeforeMapsBeijingMidnightToSameBusinessDay() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(LocalDate.of(2026, 9, 15)));

        var actual = repository.latestOnOrBefore(BEIJING_MIDNIGHT);

        assertThat(actual).contains(Instant.parse("2026-09-15T00:00:00Z"));
    }

    @Test
    void latestOnOrBeforeReceivesBeijingBusinessDayNotUtcTruncation() {
        // 前端 09-15 00:00+08:00 = UTC 09-14 16:00;若按 UTC 截取会错误地查 09-14
        java.sql.Date[] captured = new java.sql.Date[1];
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    captured[0] = (java.sql.Date) invocation.getArgument(2);
                    return List.of(LocalDate.of(2026, 9, 15));
                });

        repository.latestOnOrBefore(BEIJING_MIDNIGHT);

        assertThat(captured[0].toLocalDate()).isEqualTo(BEIJING_MIDNIGHT_DATE.toLocalDate());
    }

    @Test
    void isTradingDayMapsBeijingMidnightToSameBusinessDay() {
        java.sql.Date[] captured = new java.sql.Date[1];
        when(jdbc.query(anyString(), any(ResultSetExtractor.class),
                any(Object[].class))).thenAnswer(invocation -> {
            captured[0] = (java.sql.Date) invocation.getArgument(2);
            return Boolean.TRUE;
        });

        repository.isTradingDay(BEIJING_MIDNIGHT);

        assertThat(captured[0].toLocalDate()).isEqualTo(BEIJING_MIDNIGHT_DATE.toLocalDate());
    }
}