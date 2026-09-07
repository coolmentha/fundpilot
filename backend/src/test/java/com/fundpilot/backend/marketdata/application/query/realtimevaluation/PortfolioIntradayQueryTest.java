package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationCacheGateway;
import com.fundpilot.backend.platform.web.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioIntradayQueryTest {
    @Test
    void inaccessiblePortfolioIsRejectedInsteadOfReturningEmptySuccess() {
        var products = mock(OwnedFundProductGateway.class);
        var cache = mock(RealtimeValuationCacheGateway.class);
        var queries = new RealtimeValuationQueryHandler(cache, products);
        assertThatThrownBy(() -> queries.findIntradayForPortfolioFund(41L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(cache);
    }

    @Test
    void accessiblePortfolioWithNoEstimateHasExplicitlyEmptyData() {
        var products = mock(OwnedFundProductGateway.class);
        var cache = mock(RealtimeValuationCacheGateway.class);
        when(products.findOwnedByPortfolioFundId(41L)).thenReturn(Optional.of(
                new OwnedFundProductGateway.Product(7L, "000001", null, OwnedFundProductGateway.ProductType.ACTIVE)));
        assertThat(new RealtimeValuationQueryHandler(cache, products).findIntradayForPortfolioFund(41L)).isEmpty();
        verify(cache).findIntraday("000001");
        verify(products, never()).findOwned(anyLong());
    }
}
