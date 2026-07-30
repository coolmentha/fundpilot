package com.fundpilot.backend.marketdata.application.query.klinequery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.gateway.klinequery.IndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.navhistory.NavHistoryQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KlineQueryHandlerTest {
    @Test
    void 按组合基金查询使用组合基金所有权() {
        var products = mock(OwnedFundProductGateway.class);
        var cached = mock(IndexKlineQueryHandler.class);
        var handler = new KlineQueryHandler(products, cached, mock(IndexKlineSourceGateway.class),
                mock(NavHistoryQueryHandler.class));
        when(products.findOwnedByPortfolioFundId(9L)).thenReturn(Optional.of(
                new OwnedFundProductGateway.Product(11L, "510300", "000300.SH",
                        OwnedFundProductGateway.ProductType.INDEX)));
        when(cached.findAll("000300.SH")).thenReturn(List.of(bar("2026-06-22", "100", "105", "99", "103", 1000L)));

        var result = handler.getKlineForPortfolioFund(9L, "daily");

        assertThat(result.bars()).singleElement().extracting(KlineQueryHandler.Bar::close)
                .isEqualTo(new BigDecimal("103"));
    }

    @Test
    void weekly_聚合本地日K缓存() {
        var products = mock(OwnedFundProductGateway.class);
        var cached = mock(IndexKlineQueryHandler.class);
        var handler = new KlineQueryHandler(products, cached, mock(IndexKlineSourceGateway.class),
                mock(NavHistoryQueryHandler.class));
        when(products.findOwned(1L)).thenReturn(Optional.of(new OwnedFundProductGateway.Product(11L, "510300", "000300.SH",
                OwnedFundProductGateway.ProductType.INDEX)));
        when(cached.findAll("000300.SH")).thenReturn(List.of(
                bar("2026-06-22", "100", "105", "99", "103", 1000L),
                bar("2026-06-23", "103", "106", "102", "104", 1200L),
                bar("2026-06-24", "104", "107", "103", "106", 900L)));

        var result = handler.getKline(1L, "weekly");

        assertThat(result.chartType()).isEqualTo("kline");
        assertThat(result.bars()).singleElement().satisfies(value -> {
            assertThat(value.open()).isEqualByComparingTo("100");
            assertThat(value.high()).isEqualByComparingTo("107");
            assertThat(value.low()).isEqualByComparingTo("99");
            assertThat(value.close()).isEqualByComparingTo("106");
            assertThat(value.volume()).isEqualTo(3100L);
        });
    }

    private static IndexKlineQueryHandler.Bar bar(String date, String open, String high, String low,
                                                   String close, long volume) {
        return new IndexKlineQueryHandler.Bar(Instant.parse(date + "T00:00:00Z"), new BigDecimal(open),
                new BigDecimal(high), new BigDecimal(low), new BigDecimal(close), volume);
    }
}
