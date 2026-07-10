package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SinaTradingCalendarParser} 单元测试(task 07-09)。
 * <p>用真实新浪响应样例({@code src/test/resources/sina/klc_td_sh_sample.txt})验证 GraalVM JS 解码正确,
 * 含 1992-05-04 补丁、调休补班周末非交易日、覆盖到当年底。
 */
class SinaTradingCalendarParserTest {

    @Test
    void parse_真实新浪响应_解码出交易日列表_升序() throws IOException {
        String raw = loadSample();

        List<Instant> dates = SinaTradingCalendarParser.parse(raw);

        assertThat(dates).isNotEmpty();
        // 升序(parser 用 TreeSet)
        assertThat(dates).isSorted();
        // 最早 1990-12-19(A股开市日)
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(1990, 12, 19).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void parse_补1992_05_04_新浪历史缺失日() throws IOException {
        String raw = loadSample();
        Instant expected = LocalDate.of(1992, 5, 4).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Instant> dates = SinaTradingCalendarParser.parse(raw);

        assertThat(dates).contains(expected);
    }

    @Test
    void parse_调休补班周末非交易日_2024_09_29周日上班但休市() throws IOException {
        // 2024-09-29 周日是国庆调休上班日,但股市休市 -> 不应在交易日列表中
        String raw = loadSample();
        Instant makeUpWorkday = LocalDate.of(2024, 9, 29).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Instant> dates = SinaTradingCalendarParser.parse(raw);

        assertThat(dates).doesNotContain(makeUpWorkday);
    }

    @Test
    void parse_国庆后首个交易日_2024_10_08() throws IOException {
        String raw = loadSample();
        Instant postHoliday = LocalDate.of(2024, 10, 8).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Instant> dates = SinaTradingCalendarParser.parse(raw);

        assertThat(dates).contains(postHoliday);
    }

    @Test
    void parse_空响应抛异常() {
        assertThatThrownBy(() -> SinaTradingCalendarParser.parse(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("为空");
    }

    @Test
    void parse_null响应抛异常() {
        assertThatThrownBy(() -> SinaTradingCalendarParser.parse(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("为空");
    }

    private String loadSample() throws IOException {
        try (var in = getClass().getResourceAsStream("/sina/klc_td_sh_sample.txt")) {
            if (in == null) {
                throw new IllegalStateException("测试夹具 sina/klc_td_sh_sample.txt 未找到");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
