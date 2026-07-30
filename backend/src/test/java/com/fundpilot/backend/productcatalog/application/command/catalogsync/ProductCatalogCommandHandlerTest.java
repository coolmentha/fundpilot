package com.fundpilot.backend.productcatalog.application.command.catalogsync;

import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceFailure;
import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceGateway;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCatalogCommandHandlerTest {
    @Test
    void rejectsMissingFundCodeAsCorrectableInputFailure() {
        var handler = new ProductCatalogCommandHandler(mock(FundProductRepository.class),
                mock(ProductCatalogSourceGateway.class), mock(ProductCatalogSynchronizationWriter.class));

        assertThatThrownBy(() -> handler.ensure(" ", "测试基金", null, null))
                .isInstanceOfSatisfying(ProductCatalogFailure.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.code())
                                .isEqualTo(ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID));
    }

    @Test
    void translatesSourceFailureIntoCatalogError() {
        ProductCatalogSourceGateway source = mock(ProductCatalogSourceGateway.class);
        when(source.fetchAll()).thenThrow(new ProductCatalogSourceFailure(
                ProductCatalogSourceFailure.Kind.UNAVAILABLE, "目录暂时不可用"));
        var handler = new ProductCatalogCommandHandler(mock(FundProductRepository.class), source,
                mock(ProductCatalogSynchronizationWriter.class));

        assertThatThrownBy(handler::synchronize)
                .isInstanceOfSatisfying(ProductCatalogFailure.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.code())
                                .isEqualTo(ProductCatalogFailure.Code.PRODUCT_CATALOG_SOURCE_UNAVAILABLE));
    }
}
