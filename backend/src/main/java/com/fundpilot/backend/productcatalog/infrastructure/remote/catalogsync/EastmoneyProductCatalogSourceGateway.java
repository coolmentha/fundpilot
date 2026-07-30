package com.fundpilot.backend.productcatalog.infrastructure.remote.catalogsync;

import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceGateway;
import com.fundpilot.backend.productcatalog.application.gateway.catalogsync.ProductCatalogSourceFailure;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
class EastmoneyProductCatalogSourceGateway implements ProductCatalogSourceGateway {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final EastmoneyProductCatalogClient client;

    @Override
    public List<SourceProduct> fetchAll() {
        final String raw;
        try {
            raw = client.fetchRaw();
        } catch (RuntimeException exception) {
            throw new ProductCatalogSourceFailure(ProductCatalogSourceFailure.Kind.UNAVAILABLE,
                    "东方财富基金目录暂时不可用", exception);
        }
        int start = raw == null ? -1 : raw.indexOf('[');
        int end = raw == null ? -1 : raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new ProductCatalogSourceFailure(ProductCatalogSourceFailure.Kind.INVALID_RESPONSE,
                    "东方财富基金目录响应格式无效");
        }
        try {
            List<List<String>> rows = JSON.readValue(raw.substring(start, end + 1), new TypeReference<>() {});
            List<SourceProduct> products = new ArrayList<>(rows.size());
            for (List<String> row : rows) {
                if (row.size() >= 4 && !row.get(0).isBlank() && !row.get(2).isBlank()) {
                    products.add(new SourceProduct(row.get(0), row.get(2), row.get(3)));
                }
            }
            return products;
        } catch (RuntimeException exception) {
            throw new ProductCatalogSourceFailure(ProductCatalogSourceFailure.Kind.INVALID_RESPONSE,
                    "无法解析东方财富基金目录", exception);
        }
    }
}
