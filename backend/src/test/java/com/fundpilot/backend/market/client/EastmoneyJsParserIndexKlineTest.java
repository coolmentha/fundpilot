package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:push2his.eastmoney.com 指数 K 线解析 → {@link IndexKline}(OHLCV)。
 * <p>响应格式 JSON:{@code data.klines = ["yyyy-MM-dd,open,close,high,low,volume,...", ...]}。
 * 用 Jackson 解析(非 JS 字面量,无需 GraalVM)。
 */
class EastmoneyJsParserIndexKlineTest {

    private static final String SAMPLE = """
            {"rc":0,"rt":17,"svr":1,"lt":1,"full":1,
             "data":{"code":"000300","market":1,"name":"沪深300",
                     "klines":["2024-06-24,3500.00,3550.00,3560.00,3490.00,1000000",
                               "2024-06-25,3550.00,3600.00,3610.00,3540.00,1200000"]}}
            """;

    @Test
    void parseIndexKline() {
        IndexKline kline = EastmoneyJsParser.parseIndexKline(SAMPLE);

        assertThat(kline.bars()).hasSize(2);

        IndexKline.Bar first = kline.bars().get(0);
        assertThat(first.date()).isEqualTo(Instant.parse("2024-06-24T00:00:00Z"));
        assertThat(first.open()).isEqualByComparingTo("3500.00");
        assertThat(first.close()).isEqualByComparingTo("3550.00");
        assertThat(first.high()).isEqualByComparingTo("3560.00");
        assertThat(first.low()).isEqualByComparingTo("3490.00");
        assertThat(first.volume()).isEqualTo(1000000L);

        assertThat(kline.bars().get(1).volume()).isEqualTo(1200000L);
    }

    @Test
    void parseIndexKlineFromEmpty() {
        String empty = """
                {"data":{"klines":[]}}
                """;
        assertThat(EastmoneyJsParser.parseIndexKline(empty).bars()).isEmpty();
    }
}