package com.fundpilot.backend.market.client;

import com.fundpilot.backend.market.service.MarketDataMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExternalClientConfigTest {

    @Test
    void eastmoneyOptions_使用一秒连接三秒读取超时() {
        var options = EastmoneyClientConfig.options();

        assertThat(options.connectTimeoutMillis()).isEqualTo(1_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);
    }

    @Test
    void thsOptions_使用一秒连接三秒读取超时() {
        var options = ThsClientConfig.options();

        assertThat(options.connectTimeoutMillis()).isEqualTo(1_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);
    }

    @Test
    void marketDataSource_基金净值优先使用同花顺() throws Exception {
        CsindexMarketDataSource csindex = mock(CsindexMarketDataSource.class);
        ThsMarketDataSource ths = mock(ThsMarketDataSource.class);
        EastmoneyMarketDataSource eastmoney = mock(EastmoneyMarketDataSource.class);
        MarketDataMetrics metrics = mock(MarketDataMetrics.class);
        List<FundNavSnapshot> thsNavHistory = List.of(new FundNavSnapshot(
                Instant.parse("2026-07-20T00:00:00Z"), BigDecimal.valueOf(2.7398), BigDecimal.valueOf(2.7398)));
        when(csindex.fetchNavHistory("017093")).thenThrow(new UnsupportedOperationException());
        when(ths.fetchNavHistory("017093")).thenReturn(thsNavHistory);

        var constructor = EastmoneyClientConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        MarketDataSource source = constructor.newInstance()
                .marketDataSource(csindex, eastmoney, ths, metrics);

        assertThat(source.fetchNavHistory("017093")).isEqualTo(thsNavHistory);
        verify(ths).fetchNavHistory("017093");
        verifyNoInteractions(eastmoney);
    }
}
