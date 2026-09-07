package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indicator.MarketIndicatorCommandHandler;
import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.*;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.*;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioMarketRefreshTest {
    private final TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
    private final PublishedNavSourceGateway source = mock(PublishedNavSourceGateway.class);
    private final PublishedIndexKlineSourceGateway klines = mock(PublishedIndexKlineSourceGateway.class);
    private final MarketIndicatorRefreshCommandHandler handler = new MarketIndicatorRefreshCommandHandler(
            products, source, klines, mock(MarketIndicatorRefreshEventGateway.class),
            mock(NavPublishingCommandHandler.class), mock(IndexKlineCommandHandler.class),
            mock(IndexKlineQueryHandler.class), mock(MarketIndicatorCommandHandler.class), Clock.systemUTC(),
            mock(PublishedIndexValuationSourceGateway.class), mock(IndexValuationCommandHandler.class),
            mock(IndexValuationQueryHandler.class));

    @Test
    void trackedProductWithoutLegacyIdIsIncludedInExactlyOneScheduledBatch() {
        when(products.findAll()).thenReturn(List.of(new TrackedNavProductGateway.TrackedProduct(
                null, 7L, "000001", "测试基金", null, TrackedNavProductGateway.InvestmentTarget.STOCK)));
        when(source.fetchHistory("000001")).thenReturn(List.of(new PublishedNavSourceGateway.NavSnapshot(
                Instant.parse("2026-09-04T00:00:00Z"), BigDecimal.ONE, BigDecimal.TEN)));
        for (int batch = 0; batch < 3; batch++) handler.refreshBatch(batch);
        verify(source, times(1)).fetchHistory("000001");
    }

    @Test
    void missingOrVoidedTargetCannotSilentlySucceed() {
        assertThatThrownBy(() -> handler.refreshOneForPortfolioFund(41L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(source);
    }

    @Test
    void unsupportedTargetCannotSilentlySucceed() {
        when(products.findByPortfolioFundId(41L)).thenReturn(Optional.of(new TrackedNavProductGateway.TrackedProduct(
                null, 7L, "000001", "货币基金", null, TrackedNavProductGateway.InvestmentTarget.MONEY_MARKET)));
        assertThatThrownBy(() -> handler.refreshOneForPortfolioFund(41L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(source);
    }

    @Test
    void emptyHistoryFailsWithoutLegacyLookup() {
        when(products.findByPortfolioFundId(41L)).thenReturn(Optional.of(new TrackedNavProductGateway.TrackedProduct(
                null, 7L, "000001", "测试基金", null, TrackedNavProductGateway.InvestmentTarget.STOCK)));
        assertThatThrownBy(() -> handler.refreshOneForPortfolioFund(41L)).isInstanceOf(RuntimeException.class);
        verify(source).fetchHistory("000001");
        verify(products, never()).findByLegacyFundId(anyLong());
    }

    @Test
    void explicitRefreshDoesNotHideIndexSourceFailureBehindOldStoredValues() {
        when(products.findByPortfolioFundId(41L)).thenReturn(Optional.of(new TrackedNavProductGateway.TrackedProduct(
                null, 7L, "000001", "测试基金", "000300", TrackedNavProductGateway.InvestmentTarget.STOCK)));
        when(source.fetchHistory("000001")).thenReturn(List.of(new PublishedNavSourceGateway.NavSnapshot(
                Instant.parse("2026-09-04T00:00:00Z"), BigDecimal.ONE, BigDecimal.TEN)));
        when(klines.fetch(anyString(), anyString())).thenThrow(new IllegalStateException("source unavailable"));
        assertThatThrownBy(() -> handler.refreshOneForPortfolioFund(41L)).isInstanceOf(RuntimeException.class);
    }
}
