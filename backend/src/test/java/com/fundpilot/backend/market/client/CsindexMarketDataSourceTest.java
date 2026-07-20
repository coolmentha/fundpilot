package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link CsindexMarketDataSource} 单测:验证 secid 前缀剥离、klt 周期聚合、
 * 不支持操作抛 {@link UnsupportedOperationException}(供降级链静默跳过)。
 * <p>{@link CsindexClient} 是单方法接口,用 lambda 桩注入 canned JSON,不发真实网络请求。
 */
class CsindexMarketDataSourceTest {

    @Test
    void 深交所指数直接跳过中证源() {
        CsindexClient client = mock(CsindexClient.class);

        assertThatThrownBy(() -> new CsindexMarketDataSource(client).fetchIndexKline("0.399006", "6"))
                .isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(client);
    }

    private static final String TWO_WEEK_BARS = """
            {"code":"200","data":[
              {"tradeDate":"20260105","open":100.0,"high":115.0,"low":95.0,"close":110.0,"tradingVol":1000},
              {"tradeDate":"20260106","open":110.0,"high":125.0,"low":105.0,"close":120.0,"tradingVol":2000},
              {"tradeDate":"20260107","open":120.0,"high":135.0,"low":115.0,"close":130.0,"tradingVol":3000},
              {"tradeDate":"20260112","open":130.0,"high":145.0,"low":125.0,"close":140.0,"tradingVol":4000}
            ]}
            """;

    @Test
    void fetchIndexKline_剥离_secid_前缀_用裸代码调csindex() {
        AtomicReference<String> capturedCode = new AtomicReference<>();
        CsindexClient stub = (code, start, end) -> {
            capturedCode.set(code);
            return TWO_WEEK_BARS;
        };
        CsindexMarketDataSource source = new CsindexMarketDataSource(stub);

        IndexKline kline = source.fetchIndexKline("2.930713", "6");

        assertThat(capturedCode.get()).isEqualTo("930713"); // 不是 "2.930713"
        assertThat(kline.bars()).hasSize(4);
        assertThat(kline.bars().get(0).close()).isEqualByComparingTo("110.0");
    }

    @Test
    void fetchIndexKline_无前缀_secid_原样透传() {
        AtomicReference<String> capturedCode = new AtomicReference<>();
        CsindexClient stub = (code, start, end) -> {
            capturedCode.set(code);
            return TWO_WEEK_BARS;
        };

        new CsindexMarketDataSource(stub).fetchIndexKline("000300", "6");

        assertThat(capturedCode.get()).isEqualTo("000300");
    }

    @Test
    void fetchIndexKlineWithPeriod_klt102_聚合为周K() {
        CsindexClient stub = (code, start, end) -> TWO_WEEK_BARS;
        CsindexMarketDataSource source = new CsindexMarketDataSource(stub);

        IndexKline weekly = source.fetchIndexKlineWithPeriod("1.000300", "102", "400");

        // 4 日 K 跨两周(01-05/06/07 同周,01-12 下周)→ 2 根周 K
        assertThat(weekly.bars()).hasSize(2);
        assertThat(weekly.bars().get(0).date().toString()).isEqualTo("2026-01-07T00:00:00Z");
        assertThat(weekly.bars().get(0).volume()).isEqualTo(6000L);
    }

    @Test
    void fetchIndexKlineWithPeriod_klt101_返回日K() {
        CsindexClient stub = (code, start, end) -> TWO_WEEK_BARS;
        CsindexMarketDataSource source = new CsindexMarketDataSource(stub);

        IndexKline daily = source.fetchIndexKlineWithPeriod("1.000300", "101", "400");

        assertThat(daily.bars()).hasSize(4);
    }

    @Test
    void fetchNavHistory_抛_UnsupportedOperationException() {
        CsindexMarketDataSource source = new CsindexMarketDataSource((c, s, e) -> TWO_WEEK_BARS);

        assertThatThrownBy(() -> source.fetchNavHistory("001234"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fetchFundDict_抛_UnsupportedOperationException() {
        CsindexMarketDataSource source = new CsindexMarketDataSource((c, s, e) -> TWO_WEEK_BARS);

        assertThatThrownBy(source::fetchFundDict)
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
