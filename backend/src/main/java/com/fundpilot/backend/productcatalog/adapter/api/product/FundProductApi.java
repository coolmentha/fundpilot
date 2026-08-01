package com.fundpilot.backend.productcatalog.adapter.api.product;

import com.fundpilot.backend.productcatalog.application.command.catalogsync.ProductCatalogCommandHandler;
import com.fundpilot.backend.productcatalog.application.query.productsearch.ProductSearchQueryHandler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundProductApi {
    private final ProductSearchQueryHandler queries;
    private final ProductCatalogCommandHandler commands;

    public List<Product> search(String query) {
        return queries.search(query).stream().map(FundProductApi::from).toList();
    }

    public Optional<Product> findByCode(String fundCode) {
        return queries.findByCode(fundCode).map(FundProductApi::from);
    }

    public Optional<Product> findById(long id) {
        return queries.findById(id).map(FundProductApi::from);
    }

    public List<Product> findByIds(Set<Long> ids) {
        return queries.findByIds(ids).stream().map(FundProductApi::from).toList();
    }

    public ProductReference ensure(EnsureProduct request) {
        var result = commands.ensure(request.fundCode(), request.fundName(), request.rawName(),
                request.investmentTarget() == null ? null
                        : ProductCatalogCommandHandler.Target.valueOf(request.investmentTarget().name()));
        return new ProductReference(result.id(), result.fundCode());
    }

    /** 用户手动补填/修正基金业绩比较基准,同步到产品(issue #146)。 */
    public void updateBenchmark(long productId, String benchmarkIndexCode) {
        commands.updateBenchmark(productId, benchmarkIndexCode);
    }

    private static Product from(ProductSearchQueryHandler.ProductResult product) {
        return new Product(product.id(), product.fundCode(), product.fundName(),
                product.productType() == null ? null : ProductType.valueOf(product.productType().name()),
                product.investmentTarget() == null ? null
                        : InvestmentTarget.valueOf(product.investmentTarget().name()),
                product.benchmarkIndexCode(), product.defaultDisciplineCategory() == null ? null
                        : DisciplineSuggestion.valueOf(product.defaultDisciplineCategory().name()));
    }

    public record EnsureProduct(String fundCode, String fundName, String rawName,
                                InvestmentTarget investmentTarget) {}
    public record ProductReference(long id, String fundCode) {}
    public record Product(long id, String fundCode, String fundName, ProductType productType,
                          InvestmentTarget investmentTarget, String benchmarkIndexCode,
                          DisciplineSuggestion defaultDisciplineCategory) {}
    public enum ProductType { ETF, INDEX, INDEX_ENHANCED, ACTIVE }
    public enum InvestmentTarget { STOCK, BOND, MIXED, MONEY_MARKET, QDII, FOF, REIT, COMMODITY, ALTERNATIVE }
    public enum DisciplineSuggestion { BROAD_BASE, SECTOR, ACTIVE, MIXED }
}
