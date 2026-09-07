package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.NavPrefetchApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;

class ImportedHoldingGatewayImplTest {
    @Test
    void createsHoldingThroughPublishedModuleApis() {
        FundProductApi products = mock(FundProductApi.class);
        NavPrefetchApi prefetch = mock(NavPrefetchApi.class);
        PublishedNavApi navs = mock(PublishedNavApi.class);
        when(prefetch.fetch("017093")).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).as("prefetch must be outside the transaction").isFalse();
            return List.of();
        });
        PortfolioFundApi portfolioFunds = mock(PortfolioFundApi.class);
        PortfolioGroupingApi groups = mock(PortfolioGroupingApi.class);
        PortfolioFundOnboardingApi onboarding = mock(PortfolioFundOnboardingApi.class);
        PositionApi positions = mock(PositionApi.class);
        TransactionApi transactions = mock(TransactionApi.class);
        when(products.ensure(any())).thenReturn(new FundProductApi.ProductReference(3L, "017093"));
        when(onboarding.onboard(any())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).as("onboarding must join the item transaction").isTrue();
            return new PortfolioFundOnboardingApi.OnboardingResult(9L, 12L);
        });
        when(portfolioFunds.findOwned(1L, 9L)).thenReturn(Optional.of(new PortfolioFundApi.PortfolioFund(
                9L, 19L, 1L, 3L, PortfolioFundApi.Validity.TRACKED, true,
                new BigDecimal("0.30"), null, null, null)));
        var gateway = new ImportedHoldingGatewayImpl(products, prefetch, navs, portfolioFunds, groups, onboarding,
                positions, transactions, new TestTransactionManager(),
                mock(com.fundpilot.backend.importing.infrastructure.persistence.importitem.ImportItemReceiptStore.class));

        var result = gateway.create(1L, "017093", "示例基金", BigDecimal.TEN,
                BigDecimal.ONE, List.of("支付宝"));

        assertThat(result.legacyFundId()).isEqualTo(19L);
        verify(products).ensure(any(FundProductApi.EnsureProduct.class));
        InOrder order = inOrder(prefetch, navs, onboarding);
        order.verify(prefetch).fetch("017093");
        order.verify(navs).publishNewer(new PublishedNavApi.PublishNavs(null, 3L, "017093", List.of()));
        order.verify(onboarding).onboard(any(PortfolioFundOnboardingApi.OnboardPortfolioFund.class));
        verify(groups).assignByNames(new PortfolioGroupingApi.AssignByNames(1L, 9L, List.of("支付宝")));
    }

    static class TestTransactionManager extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
    }
}
