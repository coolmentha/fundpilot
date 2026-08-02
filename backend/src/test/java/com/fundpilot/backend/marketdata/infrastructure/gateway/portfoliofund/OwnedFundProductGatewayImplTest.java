package com.fundpilot.backend.marketdata.infrastructure.gateway.portfoliofund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OwnedFundProductGatewayImplTest {
    @Test
    void legacyFund入口忽略作废组合基金() {
        CurrentActorApi actor = mock(CurrentActorApi.class);
        PortfolioFundApi portfolioFunds = mock(PortfolioFundApi.class);
        FundProductApi products = mock(FundProductApi.class);
        when(actor.userId()).thenReturn(3L);
        when(portfolioFunds.findOwnedByLegacyFundId(3L, 41L)).thenReturn(Optional.of(voidedFund()));

        var result = new OwnedFundProductGatewayImpl(actor, portfolioFunds, products).findOwned(41L);

        assertThat(result).isEmpty();
        verifyNoInteractions(products);
    }

    @Test
    void portfolioFund入口忽略作废组合基金() {
        CurrentActorApi actor = mock(CurrentActorApi.class);
        PortfolioFundApi portfolioFunds = mock(PortfolioFundApi.class);
        FundProductApi products = mock(FundProductApi.class);
        when(actor.userId()).thenReturn(3L);
        when(portfolioFunds.findOwned(3L, 7L)).thenReturn(Optional.of(voidedFund()));

        var result = new OwnedFundProductGatewayImpl(actor, portfolioFunds, products)
                .findOwnedByPortfolioFundId(7L);

        assertThat(result).isEmpty();
        verifyNoInteractions(products);
    }

    private static PortfolioFundApi.PortfolioFund voidedFund() {
        return new PortfolioFundApi.PortfolioFund(7L, 41L, 3L, 101L,
                PortfolioFundApi.Validity.VOIDED, true, new BigDecimal("0.30"), null, 9L, "录入错误");
    }
}
