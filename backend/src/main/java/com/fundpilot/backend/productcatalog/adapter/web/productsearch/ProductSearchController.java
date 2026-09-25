package com.fundpilot.backend.productcatalog.adapter.web.productsearch;

import com.fundpilot.backend.productcatalog.application.query.productsearch.ProductSearchQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "产品搜索接口", description = "产品搜索相关操作")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductSearchController {
    private final ProductSearchQueryHandler queries;

    @Operation(summary = "按关键词搜索产品")
    @GetMapping
    public ApiResponse<List<ProductSearchQueryHandler.ProductResult>> search(
            @RequestParam("q") String query) {
        return ApiResponse.ok(queries.search(query));
    }
}
