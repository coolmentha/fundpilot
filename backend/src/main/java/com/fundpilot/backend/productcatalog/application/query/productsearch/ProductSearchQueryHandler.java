package com.fundpilot.backend.productcatalog.application.query.productsearch;

import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSearchQueryHandler {
    private static final int SEARCH_LIMIT = 20;
    private final FundProductRepository products;

    @Transactional(readOnly = true)
    public List<ProductResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return products.search(query.trim(), SEARCH_LIMIT).stream().map(ProductResult::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductResult> findByCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) return Optional.empty();
        return products.findByFundCode(fundCode.trim()).map(ProductResult::from);
    }

    @Transactional(readOnly = true)
    public Optional<ProductResult> findById(long id) {
        if (id <= 0) return Optional.empty();
        return products.findById(id).map(ProductResult::from);
    }

    @Transactional(readOnly = true)
    public List<ProductResult> findByIds(Set<Long> ids) {
        return products.findByIds(ids).stream().map(ProductResult::from).toList();
    }

    public record ProductResult(long id, String fundCode, String fundName, Type productType,
                                Target investmentTarget, String benchmarkIndexCode,
                                DisciplineSuggestion defaultDisciplineCategory) {
        static ProductResult from(FundProduct product) {
            return new ProductResult(product.id(), product.fundCode(), product.fundName(),
                    product.productType() == null ? null : Type.valueOf(product.productType().name()),
                    product.investmentTarget() == null ? null : Target.valueOf(product.investmentTarget().name()),
                    product.benchmarkIndexCode(), product.defaultDisciplineCategory() == null ? null
                            : DisciplineSuggestion.valueOf(product.defaultDisciplineCategory().name()));
        }
    }

    public enum Type { ETF, INDEX, INDEX_ENHANCED, ACTIVE }
    public enum Target { STOCK, BOND, MIXED, MONEY_MARKET, QDII, FOF, REIT, COMMODITY, ALTERNATIVE }
    public enum DisciplineSuggestion { BROAD_BASE, SECTOR, ACTIVE, MIXED }
}
