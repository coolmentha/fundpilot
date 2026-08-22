package com.fundpilot.backend.productcatalog.adapter.web.productsearch;

import com.fundpilot.backend.productcatalog.application.query.productsearch.ProductSearchQueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "产品搜索接口", description = "产品搜索相关操作")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductSearchController {
    private final ProductSearchQueryHandler queries;

    @Operation(summary = "按关键词搜索产品")
    @GetMapping
    public ProductCatalogResponse<List<ProductSearchQueryHandler.ProductResult>> search(
            @RequestParam("q") String query) {
        return ProductCatalogResponse.ok(queries.search(query));
    }

    @Schema(description = "产品搜索响应视图")
    public record ProductCatalogResponse<T>(
            @Schema(description = "请求是否成功,true 表示成功,false 表示失败", example = "true") boolean success,
            @Schema(description = "搜索结果数据列表") T data) {
        static <T> ProductCatalogResponse<T> ok(T data) { return new ProductCatalogResponse<>(true, data); }
    }
}
