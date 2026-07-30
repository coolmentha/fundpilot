package com.fundpilot.backend.marketdata.infrastructure.remote.tradingcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SinaTradingCalendarParserTest {
    @Test
    void parsesRealResponseInAscendingOrder() throws IOException {
        List<Instant> dates = SinaTradingCalendarParser.parse(loadSample());
        assertThat(dates).isSorted();
        assertThat(dates.getFirst()).isEqualTo(date(1990, 12, 19));
    }

    @Test
    void addsKnownMissingTradingDay() throws IOException {
        assertThat(SinaTradingCalendarParser.parse(loadSample())).contains(date(1992, 5, 4));
    }

    @Test
    void excludesWeekendMakeUpWorkdayAndIncludesPostHolidayTradingDay() throws IOException {
        assertThat(SinaTradingCalendarParser.parse(loadSample()))
                .doesNotContain(date(2024, 9, 29))
                .contains(date(2024, 10, 8));
    }

    @Test
    void rejectsEmptyResponse() {
        assertThatThrownBy(() -> SinaTradingCalendarParser.parse(""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("为空");
        assertThatThrownBy(() -> SinaTradingCalendarParser.parse(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("为空");
    }

    private static Instant date(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private String loadSample() throws IOException {
        try (var input = getClass().getResourceAsStream("/sina/klc_td_sh_sample.txt")) {
            if (input == null) throw new IllegalStateException("测试夹具不存在");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
