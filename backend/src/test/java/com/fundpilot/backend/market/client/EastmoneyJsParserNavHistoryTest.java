package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:pingzhongdata.js 结构提取 + Jackson 解析 → {@link FundNavSnapshot} 列表。
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
    void parseNavHistory_按时间戳关联累计净值_不依赖数组位置() {
        String shifted = """
                var Data_netWorthTrend = [{"x":1719187200000,"y":1.0000},{"x":1719273600000,"y":1.0100}];
                var Data_ACWorthTrend = [[1719273600000,2.0200],[1719187200000,2.0000]];
                """;

        List<FundNavSnapshot> snapshots = EastmoneyJsParser.parseNavHistory(shifted);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).accumulatedNav()).isEqualByComparingTo("2.0000");
        assertThat(snapshots.get(1).accumulatedNav()).isEqualByComparingTo("2.0200");
    }

    @Test
    void parseNavHistory_忽略字符串中的方括号并提取完整数组() {
        String nested = """
                var ignored = "[not an array]";
                var Data_netWorthTrend = [{"x":1719187200000,"y":1.0000,"unitMoney":"每份[测试]"}];
                var Data_ACWorthTrend = [[1719187200000,2.0000]];
                """;

        assertThat(EastmoneyJsParser.parseNavHistory(nested)).hasSize(1);
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
