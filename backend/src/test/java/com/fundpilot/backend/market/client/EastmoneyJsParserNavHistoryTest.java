package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:pingzhongdata.js GraalVM JS 解析 → {@link FundNavSnapshot} 列表。
 * <p>固化样本模拟真实响应,验证 {@code Data_netWorthTrend} (nav) +
 * {@code Data_ACWorthTrend} (accumulatedNav) 被正确解析。
 */
class EastmoneyJsParserNavHistoryTest {

    private static final String SAMPLE = """
            var Data_netWorthTrend = [{"x":1783872000000,"y":1.0000,"equityReturn":0.0,"unitMoney":""},{"x":1783958400000,"y":1.0100,"equityReturn":0.01,"unitMoney":""}];
            var Data_ACWorthTrend = [[1783872000000,2.0000],[1783958400000,2.0200]];
            """;

    @Test
    void parseNavHistory() {
        List<FundNavSnapshot> snapshots = EastmoneyJsParser.parseNavHistory(SAMPLE);

        assertThat(snapshots).hasSize(2);

        FundNavSnapshot first = snapshots.get(0);
        assertThat(first.navDate()).isEqualTo(Instant.parse("2026-07-13T00:00:00Z"));
        assertThat(first.nav()).isEqualByComparingTo("1.0000");
        assertThat(first.accumulatedNav()).isEqualByComparingTo("2.0000");

        FundNavSnapshot second = snapshots.get(1);
        assertThat(second.navDate()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(second.nav()).isEqualByComparingTo("1.0100");
        assertThat(second.accumulatedNav()).isEqualByComparingTo("2.0200");
    }

    @Test
    void parseNavHistoryFromEmptyArray() {
        String empty = """
                var Data_netWorthTrend = [];
                var Data_ACWorthTrend = [];
                """;
        assertThat(EastmoneyJsParser.parseNavHistory(empty)).isEmpty();
    }

    @Test
    void parseNavHistoryKeepsUtcMidnightDateLabel() {
        String utcMidnight = """
                var Data_netWorthTrend = [{"x":1783900800000,"y":1.0407}];
                var Data_ACWorthTrend = [[1783900800000,1.0407]];
                """;

        List<FundNavSnapshot> snapshots = EastmoneyJsParser.parseNavHistory(utcMidnight);

        assertThat(snapshots).singleElement()
                .extracting(FundNavSnapshot::navDate)
                .isEqualTo(Instant.parse("2026-07-13T00:00:00Z"));
    }
}
