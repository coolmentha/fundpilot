package com.fundpilot.backend.accounting.infrastructure.gateway;

import com.fundpilot.backend.accounting.infrastructure.gateway.transactionconfirmation.SettlementFeeGatewayImpl;
import com.fundpilot.backend.accounting.infrastructure.gateway.transactionconfirmation.SettlementNavGatewayImpl;
import com.fundpilot.backend.accounting.infrastructure.gateway.transactionledger.TradedPortfolioFundGatewayImpl;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.fee.FundFeeApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingGatewayImplTest {
    private static final Instant TRADE_DAY = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void mapsProductCatalogDiscountRateAsTheSettlementSubscriptionRate() {
        FundProductApi products = mock(FundProductApi.class);
        FundFeeApi fees = mock(FundFeeApi.class);
        when(products.findById(7L)).thenReturn(Optional.of(product()));
        when(fees.findByFundCode("001071")).thenReturn(Optional.of(new FundFeeApi.FeeSchedule(
                new BigDecimal("0.015"), new BigDecimal("0.0015"), BigDecimal.ZERO,
                List.of(new FundFeeApi.RedemptionTier(7, new BigDecimal("0.015"))), TRADE_DAY)));

        var result = new SettlementFeeGatewayImpl(products, fees).feeScheduleOf(7L);

        assertThat(result.subscriptionRate()).isEqualByComparingTo("0.0015");
        assertThat(result.redemptionRateFor(3)).isEqualByComparingTo("0.015");
    }

    @Test
    void readsOnlyTheExactPublishedNavBusinessDay() {
        PublishedNavApi navs = mock(PublishedNavApi.class);
        when(navs.history(7L, TRADE_DAY, TRADE_DAY.plusSeconds(86_400))).thenReturn(List.of(
                new PublishedNavApi.PublishedNav(7L, "001071", TRADE_DAY, new BigDecimal("1.2345"),
                        new BigDecimal("2.3456"), TRADE_DAY)));

        var result = new SettlementNavGatewayImpl(navs).unitNavOn(7L, TRADE_DAY);

        assertThat(result).contains(new BigDecimal("1.2345"));
        verify(navs).history(7L, TRADE_DAY, TRADE_DAY.plusSeconds(86_400));
    }

    @Test
    void marksVoidedPortfolioFundsAsNotTradable() {
        PortfolioFundApi portfolioFunds = mock(PortfolioFundApi.class);
        when(portfolioFunds.findById(3L)).thenReturn(Optional.of(new PortfolioFundApi.PortfolioFund(
                3L, 12L, 5L, 7L, PortfolioFundApi.Validity.VOIDED, false, null,
                TRADE_DAY, 5L, "录入错误")));

        var result = new TradedPortfolioFundGatewayImpl(portfolioFunds).find(3L);

        assertThat(result).hasValueSatisfying(fund -> {
            assertThat(fund.ownerId()).isEqualTo(5L);
            assertThat(fund.legacyFundId()).isEqualTo(12L);
            assertThat(fund.tradable()).isFalse();
        });
    }

    private static FundProductApi.Product product() {
        return new FundProductApi.Product(7L, "001071", "华安媒体互联网混合", null, null,
                null, null);
    }
}
