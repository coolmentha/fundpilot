package com.fundpilot.backend.market.client;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsEtfSpotParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThsEtfSpotParserTest {

    @Test
    void parse_提取最近确认单位净值而不是当日增长率() {
        var result = ThsEtfSpotParser.parse("""
                g({"data":{"data":{"510300":{"code":"510300","newnet":"4.4000",
                "newdate":"2026-08-06","rate":"1.23"}}}})
                """);

        assertThat(result).containsKey("510300");
        assertThat(result.get("510300").nav()).isEqualByComparingTo("4.4000");
        assertThat(result.get("510300").navDate()).isEqualTo("2026-08-06");
    }

    @Test
    void parse_缺少JSONP数据返回空结果() {
        assertThat(ThsEtfSpotParser.parse("<html></html>")).isEmpty();
    }
}
