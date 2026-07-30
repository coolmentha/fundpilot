package com.fundpilot.backend.productcatalog.adapter.web.productsearch;

import com.fundpilot.backend.productcatalog.application.query.productsearch.ProductSearchQueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductSearchController {
    private final ProductSearchQueryHandler queries;

    @GetMapping
    public ProductCatalogResponse<List<ProductSearchQueryHandler.ProductResult>> search(
            @RequestParam("q") String query) {
        return ProductCatalogResponse.ok(queries.search(query));
    }

    public record ProductCatalogResponse<T>(boolean success, T data) {
        static <T> ProductCatalogResponse<T> ok(T data) { return new ProductCatalogResponse<>(true, data); }
    }
}
