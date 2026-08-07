package com.fundpilot.backend.market.client;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyEtfSpotParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EastmoneyEtfSpotParserTest {

    @Test
    void parse_提取IOPV更新时间和数据日期() {
        long updatedAt = Instant.parse("2026-08-07T03:40:00Z").getEpochSecond();

        EastmoneyEtfSpotParser.Page result = EastmoneyEtfSpotParser.parse("""
                {"data":{"total":1,"diff":{"0":{"f12":"510300","f441":"4.5000",
                "f124":%d,"f297":"20260807"}}}}
                """.formatted(updatedAt));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.quotes().get("510300").iopv()).isEqualByComparingTo("4.5000");
        assertThat(result.quotes().get("510300").updatedAt()).isEqualTo(Instant.ofEpochSecond(updatedAt));
        assertThat(result.quotes().get("510300").dataDate()).isEqualTo("20260807");
    }

    @Test
    void parse_空数据返回空页() {
        assertThat(EastmoneyEtfSpotParser.parse(null).quotes()).isEmpty();
        assertThat(EastmoneyEtfSpotParser.parse("{\"data\":null}").quotes()).isEmpty();
    }

    @Test
    void parse_损坏JSON标记解析失败() {
        assertThatThrownBy(() -> EastmoneyEtfSpotParser.parse("{broken"))
                .isInstanceOf(IllegalStateException.class);
    }
}
