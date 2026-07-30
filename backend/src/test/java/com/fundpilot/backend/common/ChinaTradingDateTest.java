package com.fundpilot.backend.sharedkernel.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChinaTradingDateTest {

    @Test
    void toUtcDate_北京时间凌晨仍映射到当天Utc零点() {
        Instant beijingEarlyMorning = Instant.parse("2026-07-09T17:30:00Z"); // 北京时间 07-10 01:30

        assertThat(ChinaTradingDate.toUtcDate(beijingEarlyMorning))
                .isEqualTo(Instant.parse("2026-07-10T00:00:00Z"));
    }

    @Test
    void previousUtcDate_北京时间周二凌晨映射到周一Utc零点() {
        Instant tuesdayAtThree = Instant.parse("2026-07-13T19:00:00Z");

        assertThat(ChinaTradingDate.previousUtcDate(tuesdayAtThree))
                .isEqualTo(Instant.parse("2026-07-13T00:00:00Z"));
    }
}
