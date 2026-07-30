package com.fundpilot.backend.productcatalog.adapter.web.catalogsync;

import com.fundpilot.backend.productcatalog.application.command.catalogsync.ProductCatalogCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products/catalog")
@RequiredArgsConstructor
public class CatalogSynchronizationController {
    private final ProductCatalogCommandHandler commands;

    @PostMapping("/sync")
    public SynchronizationView synchronize() {
        return new SynchronizationView(true, commands.synchronize());
    }

    public record SynchronizationView(boolean success, int synchronizedProducts) {}
}
