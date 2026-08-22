package com.fundpilot.backend.productcatalog.adapter.web.catalogsync;

import com.fundpilot.backend.productcatalog.application.command.catalogsync.ProductCatalogCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "管理 - 目录同步接口", description = "目录同步相关操作")
@RestController
@RequestMapping("/api/admin/products/catalog")
@RequiredArgsConstructor
public class CatalogSynchronizationController {
    private final ProductCatalogCommandHandler commands;

    @Operation(summary = "同步产品目录")
    @PostMapping("/sync")
    public SynchronizationView synchronize() {
        return new SynchronizationView(true, commands.synchronize());
    }

    @Schema(description = "目录同步结果视图")
    public record SynchronizationView(
            @Schema(description = "同步是否成功,true 表示成功,false 表示失败", example = "true") boolean success,
            @Schema(description = "本次同步的产品数量", example = "128") int synchronizedProducts) {}
}
