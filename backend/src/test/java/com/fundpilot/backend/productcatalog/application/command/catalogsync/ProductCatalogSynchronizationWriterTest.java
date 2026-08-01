package com.fundpilot.backend.productcatalog.application.command.catalogsync;

import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceGateway.SourceProduct;
import com.fundpilot.backend.productcatalog.domain.product.DefaultDisciplineCategory;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import com.fundpilot.backend.productcatalog.domain.product.ProductType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCatalogSynchronizationWriterTest {
    @Test
    void deduplicatesSourceCodesAndBatchUpdatesExistingProducts() {
        FundProductRepository products = mock(FundProductRepository.class);
        FundProduct existing = FundProduct.rehydrate(1L, "510300", "旧名称", null,
                ProductType.ETF, null, "000300.SH", false, DefaultDisciplineCategory.BROAD_BASE);
        when(products.findByFundCodes(anySet())).thenReturn(List.of(existing));
        var writer = new ProductCatalogSynchronizationWriter(products);

        int count = writer.synchronize(List.of(
                new SourceProduct("510300", "沪深300ETF", "ETF"),
                new SourceProduct("510300", "沪深300ETF联接", "指数型"),
                new SourceProduct("163406", "兴全合宜混合A", "混合型")));

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<List<FundProduct>> captor = ArgumentCaptor.captor();
        verify(products).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(FundProduct::fundCode)
                .containsExactlyInAnyOrder("510300", "163406");
        assertThat(existing.fundName()).isEqualTo("沪深300ETF联接");
    }

    @Test
    void 用户修正的业绩基准不被目录同步回退() {
        FundProductRepository products = mock(FundProductRepository.class);
        FundProduct existing = FundProduct.rehydrate(1L, "510300", "沪深300ETF", null,
                ProductType.ETF, null, "000300.SH", true, DefaultDisciplineCategory.BROAD_BASE);
        when(products.findByFundCodes(anySet())).thenReturn(List.of(existing));
        var writer = new ProductCatalogSynchronizationWriter(products);

        writer.synchronize(List.of(new SourceProduct("510300", "沪深300ETF", "ETF")));

        assertThat(existing.benchmarkIndexCode()).isEqualTo("000300.SH");
        assertThat(existing.benchmarkCustomized()).isTrue();
    }

    @Test
    void 未修正的业绩基准仍按目录分类刷新() {
        FundProductRepository products = mock(FundProductRepository.class);
        FundProduct existing = FundProduct.rehydrate(1L, "510300", "沪深300ETF", null,
                ProductType.ETF, null, "000300.SH", false, DefaultDisciplineCategory.BROAD_BASE);
        when(products.findByFundCodes(anySet())).thenReturn(List.of(existing));
        var writer = new ProductCatalogSynchronizationWriter(products);

        writer.synchronize(List.of(new SourceProduct("510300", "沪深300ETF", "ETF")));

        assertThat(existing.benchmarkCustomized()).isFalse();
    }
}
