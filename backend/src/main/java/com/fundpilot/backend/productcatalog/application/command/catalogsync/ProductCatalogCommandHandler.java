package com.fundpilot.backend.productcatalog.application.command.catalogsync;

import com.fundpilot.backend.productcatalog.application.gateway.benchmark.BenchmarkIndexResolver;
import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceGateway;
import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceFailure;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import com.fundpilot.backend.productcatalog.domain.product.InvestmentTarget;
import com.fundpilot.backend.productcatalog.domain.product.ProductClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogCommandHandler {
    private final FundProductRepository products;
    private final ProductCatalogSourceGateway source;
    private final ProductCatalogSynchronizationWriter synchronizationWriter;
    private final BenchmarkIndexResolver benchmarkResolver;

    public int synchronize() {
        try {
            var records = source.fetchAll();
            if (records == null || records.isEmpty()) return 0;
            return synchronizationWriter.synchronize(records);
        } catch (ProductCatalogSourceFailure failure) {
            var code = failure.kind() == ProductCatalogSourceFailure.Kind.INVALID_RESPONSE
                    ? ProductCatalogFailure.Code.PRODUCT_CATALOG_RESPONSE_INVALID
                    : ProductCatalogFailure.Code.PRODUCT_CATALOG_SOURCE_UNAVAILABLE;
            throw new ProductCatalogFailure(code, failure.getMessage(), failure);
        }
    }

    @Transactional
    public ProductResult ensure(String fundCode, String fundName, String rawName,
                                Target investmentTarget) {
        String code = normalizeCode(fundCode);
        if (fundName == null || fundName.isBlank()) {
            throw new ProductCatalogFailure(ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID,
                    "基金名称不能为空");
        }
        FundProduct product = products.findByFundCode(code).orElseGet(() -> {
            var classification = ProductClassifier.classify(fundName);
            // 单只创建时优先用接口返回的真实跟踪标的,避免名称关键词猜错(如 008888 被误判为中证半导体);
            // 全量目录同步(synchronize)不走这里,避免批量外部调用触发限流。
            String benchmark = benchmarkResolver.resolve(code).orElse(classification.benchmarkIndexCode());
            return FundProduct.create(code, fundName, rawName, classification.productType(),
                    investmentTarget == null ? null : InvestmentTarget.valueOf(investmentTarget.name()),
                    benchmark, classification.defaultDisciplineCategory());
        });
        try {
            product.identifyInvestmentTarget(investmentTarget == null ? null
                    : InvestmentTarget.valueOf(investmentTarget.name()));
        } catch (IllegalStateException exception) {
            throw new ProductCatalogFailure(ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID,
                    exception.getMessage(), exception);
        }
        return ProductResult.from(products.save(product));
    }

    @Transactional
    public ProductResult updateBenchmark(long productId, String benchmarkIndexCode) {
        FundProduct product = products.findById(productId).orElseThrow(() ->
                new ProductCatalogFailure(ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID,
                        "基金产品不存在: " + productId));
        product.updateBenchmarkIndexCode(benchmarkIndexCode);
        return ProductResult.from(products.save(product));
    }

    private String normalizeCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            throw new ProductCatalogFailure(ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID,
                    "基金代码不能为空");
        }
        return fundCode.trim();
    }

    public record ProductResult(long id, String fundCode) {
        static ProductResult from(FundProduct product) {
            return new ProductResult(product.id(), product.fundCode());
        }
    }

    public enum Target {
        STOCK, BOND, MIXED, MONEY_MARKET, QDII, FOF, REIT, COMMODITY, ALTERNATIVE
    }
}
