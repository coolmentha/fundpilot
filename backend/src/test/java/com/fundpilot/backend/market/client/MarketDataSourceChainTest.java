package com.fundpilot.backend.market.client;

import com.fundpilot.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MarketDataSourceChain 降级链单测:聚焦 {@code fetchIndexKlineWithPeriod} 是否透传 klt。
 * <p>历史 bug:chain 未 override 该方法,走接口 default → 调 {@code fetchIndexKline}(忽略 klt,恒日K),
 * 导致日/周/月 K 都一样。本测试用桩 source 验证 klt 透传 + 失败降级。
 */
class MarketDataSourceChainTest {

    /** 桩 source:记录收到的 klt,可控制抛异常或返回固定 kline。 */
    static class StubSource implements MarketDataSource {
        String name;
        boolean fail;
        boolean empty;
        String receivedKlt;
        String receivedLmt;

        StubSource(String name, boolean fail) {
            this.name = name;
            this.fail = fail;
        }

        @Override
        public List<FundNavSnapshot> fetchNavHistory(String fundCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FundDictEntry> fetchFundDict() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IndexKline fetchIndexKline(String indexCode, String range) {
            throw new UnsupportedOperationException("不应走 default 日K路径");
        }

        @Override
        public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
            receivedKlt = klt;
            receivedLmt = lmt;
            if (fail) throw new RuntimeException(name + " 失败");
            if (empty) return new IndexKline(List.of());
            return new IndexKline(List.of(new IndexKline.Bar(
                    Instant.parse("2026-01-01T00:00:00Z"),
                    java.math.BigDecimal.ONE, java.math.BigDecimal.TEN,
                    java.math.BigDecimal.TEN, java.math.BigDecimal.ONE, 100L)));
        }
    }

    @Test
    void fetchIndexKlineWithPeriod_透传_klt_到_source_不走_default_日K() {
        StubSource source = new StubSource("eastmoney", false);
        MarketDataSourceChain chain = new MarketDataSourceChain(List.of(source));

        chain.fetchIndexKlineWithPeriod("1.000300", "102", "400");

        assertThat(source.receivedKlt).isEqualTo("102"); // 周 K,不是默认 101
        assertThat(source.receivedLmt).isEqualTo("400");
    }

    @Test
    void fetchIndexKlineWithPeriod_首个_source_失败时_降级到下一个_且透传_klt() {
        StubSource failSource = new StubSource("ths", true);
        StubSource okSource = new StubSource("eastmoney", false);
        MarketDataSourceChain chain = new MarketDataSourceChain(List.of(failSource, okSource));

        IndexKline result = chain.fetchIndexKlineWithPeriod("1.000300", "103", "400");

        assertThat(result.bars()).hasSize(1);
        assertThat(okSource.receivedKlt).isEqualTo("103"); // 月 K 透传到兜底 source
    }

    @Test
    void fetchIndexKlineWithPeriod_全部失败抛_MARKET_DATA_ALL_SOURCES_FAILED() {
        StubSource a = new StubSource("a", true);
        StubSource b = new StubSource("b", true);
        MarketDataSourceChain chain = new MarketDataSourceChain(List.of(a, b));

        assertThatThrownBy(() -> chain.fetchIndexKlineWithPeriod("1.000300", "102", "400"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fetchIndexKlineWithPeriod_月K_103_也透传() {
        StubSource source = new StubSource("eastmoney", false);
        MarketDataSourceChain chain = new MarketDataSourceChain(List.of(source));

        chain.fetchIndexKlineWithPeriod("2.930713", "103", "400");

        assertThat(source.receivedKlt).isEqualTo("103");
    }

    @Test
    void fetchIndexKlineWithPeriod_首个_source_返回空结果时_降级到下一个() {
        StubSource emptySource = new StubSource("empty", false);
        emptySource.empty = true;
        StubSource okSource = new StubSource("ok", false);
        MarketDataSourceChain chain = new MarketDataSourceChain(List.of(emptySource, okSource));

        IndexKline result = chain.fetchIndexKlineWithPeriod("1.000300", "101", "400");

        assertThat(result.bars()).hasSize(1);
        assertThat(okSource.receivedKlt).isEqualTo("101");
    }
}
