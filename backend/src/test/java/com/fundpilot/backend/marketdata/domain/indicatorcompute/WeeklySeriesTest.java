package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklySeriesTest {

    @Test
    void 按周日结束聚合且取每周最后一个交易日取值() {
        List<Instant> dates = List.of(
                Instant.parse("2026-01-05T00:00:00Z"),
                Instant.parse("2026-01-09T00:00:00Z"),
                Instant.parse("2026-01-12T00:00:00Z"));
        List<BigDecimal> values = List.of(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"));

        List<WeeklySeries.WeekPoint> weekly = WeeklySeries.lastValuePerWeek(dates, values);

        assertThat(weekly).hasSize(2);
        assertThat(weekly.get(0).weekEnd()).isEqualTo(Instant.parse("2026-01-11T00:00:00Z"));
        assertThat(weekly.get(0).value()).isEqualByComparingTo("2");
        assertThat(weekly.get(1).weekEnd()).isEqualTo(Instant.parse("2026-01-18T00:00:00Z"));
        assertThat(weekly.get(1).value()).isEqualByComparingTo("3");
    }

    @Test
    void 同周内最后一条交易日的取值覆盖前面的取值() {
        List<Instant> dates = List.of(
                Instant.parse("2026-01-05T00:00:00Z"),
                Instant.parse("2026-01-06T00:00:00Z"),
                Instant.parse("2026-01-07T00:00:00Z"));
        List<BigDecimal> values = List.of(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"));

        List<WeeklySeries.WeekPoint> weekly = WeeklySeries.lastValuePerWeek(dates, values);

        assertThat(weekly).hasSize(1);
        assertThat(weekly.getFirst().value()).isEqualByComparingTo("3");
    }

    @Test
    void 日期与取值长度不一致被拒绝() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                WeeklySeries.lastValuePerWeek(List.of(Instant.parse("2026-01-05T00:00:00Z")), List.of()));
    }
}