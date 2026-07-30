package com.fundpilot.backend.productcatalog.application.command.catalogsync;

import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceGateway.SourceProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import com.fundpilot.backend.productcatalog.domain.product.ProductClassifier;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ProductCatalogSynchronizationWriter {
    private final FundProductRepository products;

    @Transactional
    int synchronize(List<SourceProduct> records) {
        Map<String, SourceProduct> recordsByCode = records.stream()
                .collect(Collectors.toMap(record -> normalizeCode(record.fundCode()),
                        Function.identity(), (first, latest) -> latest));
        Map<String, FundProduct> existingByCode = products.findByFundCodes(recordsByCode.keySet()).stream()
                .collect(Collectors.toMap(FundProduct::fundCode, Function.identity()));
        List<FundProduct> updates = recordsByCode.entrySet().stream().map(entry -> {
            SourceProduct record = entry.getValue();
            var classification = ProductClassifier.classify(record.fundName());
            FundProduct product = existingByCode.get(entry.getKey());
            if (product == null) {
                return FundProduct.create(entry.getKey(), record.fundName(), record.rawName(),
                        classification.productType(), null, classification.benchmarkIndexCode(),
                        classification.defaultDisciplineCategory());
            }
            product.refreshCatalogFacts(record.fundName(), record.rawName(), classification.productType(),
                    classification.benchmarkIndexCode(), classification.defaultDisciplineCategory());
            return product;
        }).toList();
        products.saveAll(updates);
        return updates.size();
    }

    private String normalizeCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            throw new ProductCatalogFailure(ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID,
                    "基金代码不能为空");
        }
        return fundCode.trim();
    }
}
