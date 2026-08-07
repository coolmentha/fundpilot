package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TencentIndexMarketDataSourceTest {

    private static final String RAW = """
            kline_dayqfq={"code":0,"data":{"sh000300":{"day":[
            ["2026-01-02","100","101","102","99","100",{}],
            ["2026-01-05","101","103","104","100","200",{}]
            ]}}}
            """;

    @Test
    void toTencentSymbol_按交易所secid映射() {
        assertThat(TencentIndexMarketDataSource.toTencentSymbol("1.000300")).isEqualTo("sh000300");
        assertThat(TencentIndexMarketDataSource.toTencentSymbol("0.399001")).isEqualTo("sz399001");
        assertThat(TencentIndexMarketDataSource.toTencentSymbol("000300")).isEqualTo("sh000300");
    }

    @Test
    void CSI指数直接跳过腾讯源() {
        TencentIndexClient client = mock(TencentIndexClient.class);

        assertThatThrownBy(() -> new TencentIndexMarketDataSource(client)
                .fetchIndexKline("2.930713", "400"))
                .isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(client);
    }

    @Test
    void fetchIndexKline_传腾讯symbol并保留最近limit根() {
        AtomicReference<String> symbol = new AtomicReference<>();
        TencentIndexClient client = (value, start, end) -> {
            symbol.set(value);
            return RAW;
        };

        IndexKline kline = new TencentIndexMarketDataSource(client)
                .fetchIndexKline("1.000300", "1");

        assertThat(symbol.get()).isEqualTo("sh000300");
        assertThat(kline.bars()).hasSize(1);
        assertThat(kline.bars().getFirst().close()).isEqualByComparingTo("103");
        assertThat(kline.bars().getFirst().date()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
    }
}
