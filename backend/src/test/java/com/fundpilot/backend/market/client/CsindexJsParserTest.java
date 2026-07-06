package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CsindexJsParser} 单测:借鉴 akshare {@code stock_zh_index_hist_csindex} 的中证指数公司接口
 * 响应解析 + 日 K 聚合周/月 K。
 * <p>响应 JSON {@code data[]} 元素含 tradeDate(yyyyMMdd)/open/high/low/close/tradingVol(科学计数法股数)。
 */
class CsindexJsParserTest {

    private static final String SAMPLE = """
            {"code":"200","msg":"Success","data":[
              {"tradeDate":"20260105","indexCode":"930713","open":5337.3,"high":5454.83,"low":5337.3,"close":5451.43,"tradingVol":2.029886206E9},
              {"tradeDate":"20260106","indexCode":"930713","open":5411.91,"high":5497.88,"low":5388.29,"close":5491.38,"tradingVol":2.340700179E9}
            ]}
            """;

    @Test
    void parseIndexKline_解析_OHLCV_与_yyyyMMdd_日期() {
        IndexKline kline = CsindexJsParser.parseIndexKline(SAMPLE, "930713");

        assertThat(kline.bars()).hasSize(2);

        IndexKline.Bar first = kline.bars().get(0);
        assertThat(first.date()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
        assertThat(first.open()).isEqualByComparingTo("5337.3");
        assertThat(first.close()).isEqualByComparingTo("5451.43");
        assertThat(first.high()).isEqualByComparingTo("5454.83");
        assertThat(first.low()).isEqualByComparingTo("5337.3");
        // 科学计数法 2.029886206E9 → 2029886206
        assertThat(first.volume()).isEqualTo(2029886206L);

        assertThat(kline.bars().get(1).volume()).isEqualTo(2340700179L);
    }

    @Test
    void parseIndexKline_空_data_抛异常让降级链回退() {
        String empty = """
                {"code":"200","msg":"Success","data":[]}
                """;
        assertThatThrownBy(() -> CsindexJsParser.parseIndexKline(empty, "399006"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("399006");
    }

    @Test
    void parseIndexKline_跳过周末_bar_消除_startDate_边界伪影() {
        // 2026-01-03 周六(复刻下一交易日的伪影)+ 2026-01-05 周一(真)→ 周六应被过滤
        String withWeekend = """
                {"data":[
                  {"tradeDate":"20260103","open":5337.3,"high":5454.83,"low":5337.3,"close":5451.43,"tradingVol":100},
                  {"tradeDate":"20260105","open":5337.3,"high":5454.83,"low":5337.3,"close":5451.43,"tradingVol":200}
                ]}
                """;
        IndexKline kline = CsindexJsParser.parseIndexKline(withWeekend, "930713");

        assertThat(kline.bars()).hasSize(1);
        assertThat(kline.bars().get(0).date()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
    }

    @Test
    void aggregate_周K_按周一分组_open首_high_max_low_min_close末_vol_sum_date末日() {
        // 2026-01-05(周一)/06/07 同周;2026-01-12(周一)下周
        IndexKline daily = new IndexKline(java.util.List.of(
                bar("2026-01-05", 100, 110, 115, 95, 1000),
                bar("2026-01-06", 110, 120, 125, 105, 2000),
                bar("2026-01-07", 120, 130, 135, 115, 3000),
                bar("2026-01-12", 130, 140, 145, 125, 4000)));

        IndexKline weekly = CsindexJsParser.aggregate(daily, "weekly");

        assertThat(weekly.bars()).hasSize(2);
        IndexKline.Bar w1 = weekly.bars().get(0);
        assertThat(w1.date()).isEqualTo(Instant.parse("2026-01-07T00:00:00Z")); // 蜡烛绘在周期末
        assertThat(w1.open()).isEqualByComparingTo("100");   // 首日 open
        assertThat(w1.close()).isEqualByComparingTo("130");  // 末日 close
        assertThat(w1.high()).isEqualByComparingTo("135");   // max
        assertThat(w1.low()).isEqualByComparingTo("95");     // min
        assertThat(w1.volume()).isEqualTo(6000L);            // sum
    }

    @Test
    void aggregate_月K_按月首分组() {
        // 2026-01-30 / 2026-01-31 同月;2026-02-02 下月
        IndexKline daily = new IndexKline(java.util.List.of(
                bar("2026-01-30", 100, 110, 115, 95, 1000),
                bar("2026-01-31", 110, 120, 125, 105, 2000),
                bar("2026-02-02", 120, 130, 135, 115, 3000)));

        IndexKline monthly = CsindexJsParser.aggregate(daily, "monthly");

        assertThat(monthly.bars()).hasSize(2);
        assertThat(monthly.bars().get(0).date()).isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));
        assertThat(monthly.bars().get(0).volume()).isEqualTo(3000L);
        assertThat(monthly.bars().get(1).date()).isEqualTo(Instant.parse("2026-02-02T00:00:00Z"));
    }

    @Test
    void aggregate_daily_原样返回() {
        IndexKline daily = new IndexKline(java.util.List.of(
                bar("2026-01-05", 100, 110, 115, 95, 1000)));

        assertThat(CsindexJsParser.aggregate(daily, "daily")).isSameAs(daily);
        assertThat(CsindexJsParser.aggregate(daily, null)).isSameAs(daily);
    }

    private static IndexKline.Bar bar(String date, double open, double close,
                                      double high, double low, long vol) {
        return new IndexKline.Bar(
                Instant.parse(date + "T00:00:00Z"),
                BigDecimal.valueOf(open), BigDecimal.valueOf(close),
                BigDecimal.valueOf(high), BigDecimal.valueOf(low), vol);
    }
}
