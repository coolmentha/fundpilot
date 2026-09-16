package com.fundpilot.backend.productcatalog.application.command.catalogsync;

import com.fundpilot.backend.productcatalog.application.gateway.benchmark.BenchmarkIndexResolver;
import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceFailure;
import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceGateway;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCatalogCommandHandlerTest {
    @Test
    void rejectsMissingFundCodeAsCorrectableInputFailure() {
        var handler = new ProductCatalogCommandHandler(mock(FundProductRepository.class),
                mock(ProductCatalogSourceGateway.class), mock(ProductCatalogSynchronizationWriter.class),
                mock(BenchmarkIndexResolver.class));

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
                mock(ProductCatalogSynchronizationWriter.class), mock(BenchmarkIndexResolver.class));

        assertThatThrownBy(handler::synchronize)
                .isInstanceOfSatisfying(ProductCatalogFailure.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.code())
                                .isEqualTo(ProductCatalogFailure.Code.PRODUCT_CATALOG_SOURCE_UNAVAILABLE));
    }

    @Test
    void creatingFundPrefersResolvedBenchmarkOverKeywordGuess() {
        var products = mock(FundProductRepository.class);
        when(products.findByFundCode("008888")).thenReturn(Optional.empty());
        when(products.save(org.mockito.ArgumentMatchers.any(FundProduct.class)))
                .thenAnswer(invocation -> rehydrate(invocation.getArgument(0)));
        var resolver = mock(BenchmarkIndexResolver.class);
        when(resolver.resolve("008888")).thenReturn(Optional.of("980017.SZ"));
        var handler = new ProductCatalogCommandHandler(products,
                mock(ProductCatalogSourceGateway.class), mock(ProductCatalogSynchronizationWriter.class),
                resolver);

        handler.ensure("008888", "华夏国证半导体芯片ETF联接C", null, null);

        verify(resolver).resolve("008888");
        var captor = org.mockito.ArgumentCaptor.forClass(FundProduct.class);
        verify(products).save(captor.capture());
        // 名称含"半导体"会被关键词分类猜成 931865.CSI,但接口解析优先,应存 980017.SZ
        assertThat(captor.getValue().benchmarkIndexCode()).isEqualTo("980017.SZ");
    }

    @Test
    void creatingFundFallsBackToKeywordGuessWhenResolverEmpty() {
        var products = mock(FundProductRepository.class);
        when(products.findByFundCode("510300")).thenReturn(Optional.empty());
        when(products.save(org.mockito.ArgumentMatchers.any(FundProduct.class)))
                .thenAnswer(invocation -> rehydrate(invocation.getArgument(0)));
        var resolver = mock(BenchmarkIndexResolver.class);
        when(resolver.resolve("510300")).thenReturn(Optional.empty());
        var handler = new ProductCatalogCommandHandler(products,
                mock(ProductCatalogSourceGateway.class), mock(ProductCatalogSynchronizationWriter.class),
                resolver);

        handler.ensure("510300", "易方达沪深300ETF", null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(FundProduct.class);
        verify(products).save(captor.capture());
        assertThat(captor.getValue().benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    /** 模拟 JPA save 后回填主键:构造带 id 的副本,避免 ProductResult.from 解包 null 主键。 */
    private static FundProduct rehydrate(FundProduct product) {
        return FundProduct.rehydrate(1L, product.fundCode(), product.fundName(), product.rawName(),
                product.productType(), product.investmentTarget(), product.benchmarkIndexCode(),
                product.benchmarkCustomized(), product.defaultDisciplineCategory());
    }
}
