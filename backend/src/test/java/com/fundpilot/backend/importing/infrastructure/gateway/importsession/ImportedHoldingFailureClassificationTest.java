package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportFailure;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.infrastructure.persistence.importitem.ImportItemReceiptStore;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.NavPrefetchApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportedHoldingFailureClassificationTest {
    private FundProductApi products;
    private NavPrefetchApi navPrefetch;
    private PortfolioFundApi portfolioFunds;
    private PositionApi positions;
    private ImportedHoldingGateway gateway;

    @BeforeEach
    void setUp() {
        products = mock(FundProductApi.class);
        navPrefetch = mock(NavPrefetchApi.class);
        portfolioFunds = mock(PortfolioFundApi.class);
        positions = mock(PositionApi.class);
        gateway = new ImportedHoldingGatewayImpl(products, navPrefetch, mock(PublishedNavApi.class), portfolioFunds,
                mock(PortfolioGroupingApi.class), mock(PortfolioFundOnboardingApi.class), positions,
                mock(TransactionApi.class), new ImportedHoldingGatewayImplTest.TestTransactionManager(),
                mock(ImportItemReceiptStore.class));
    }

    @Test
    void navPrefetchFailureIsADependencyFailure() {
        when(navPrefetch.fetch("017093")).thenThrow(new IllegalStateException("remote token=secret"));

        assertFailure(request(null), YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_DEPENDENCY_FAILED);
    }

    @Test
    void existingHoldingWithoutModeIsAConflict() {
        when(products.findByCode("017093")).thenReturn(Optional.of(
                new FundProductApi.Product(3L, "017093", "示例基金", null, null, null, null)));
        when(portfolioFunds.findByOwner(1L)).thenReturn(List.of(new PortfolioFundApi.PortfolioFund(
                9L, null, 1L, 3L, PortfolioFundApi.Validity.TRACKED, true,
                new BigDecimal("0.30"), null, null, null)));
        when(positions.findOwned(1L, 9L)).thenReturn(Optional.empty());

        assertFailure(request(null), YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_CONFLICT);
    }

    private void assertFailure(ImportedHoldingGateway.ItemRequest request, YangjibaoImportFailure.Code code) {
        assertThatThrownBy(() -> gateway.importItem(request))
                .isInstanceOfSatisfying(YangjibaoImportFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private ImportedHoldingGateway.ItemRequest request(ImportedHoldingGateway.ExistingMode mode) {
        return new ImportedHoldingGateway.ItemRequest(1L, "session", "account:holding", "017093", "示例基金",
                BigDecimal.TEN, BigDecimal.ONE, List.of("支付宝"), mode);
    }
}
