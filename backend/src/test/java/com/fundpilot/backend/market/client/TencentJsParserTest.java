package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TencentJsParserTest {

    @Test
    void parseIndexKline_解析AKShare腾讯日线字段() {
        String raw = """
                kline_dayqfq={"code":0,"data":{"sh000300":{"day":[
                ["2026-01-02","100.10","101.20","102.30","99.90","12345.00",{},"1.10","1.10","1.10","0.00"]
                ]}}}
                """;

        IndexKline kline = TencentJsParser.parseIndexKline(raw, "sh000300");

        assertThat(kline.bars()).hasSize(1);
        IndexKline.Bar bar = kline.bars().getFirst();
        assertThat(bar.date()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(bar.open()).isEqualByComparingTo("100.10");
        assertThat(bar.close()).isEqualByComparingTo("101.20");
        assertThat(bar.high()).isEqualByComparingTo("102.30");
        assertThat(bar.low()).isEqualByComparingTo("99.90");
        assertThat(bar.volume()).isEqualTo(12345L);
    }

    @Test
    void parseIndexKline_腾讯不覆盖指数时返回空结果触发降级() {
        String raw = "kline_dayqfq={\"code\":0,\"data\":{\"sh930713\":{\"day\":[]}}}";

        assertThat(TencentJsParser.parseIndexKline(raw, "sh930713").bars()).isEmpty();
    }

    @Test
    void parseIndexKline_缺少腾讯变量时抛解析错误() {
        assertThatThrownBy(() -> TencentJsParser.parseIndexKline("{}", "sh000300"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kline_dayqfq");
    }
}
