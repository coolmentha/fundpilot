package com.fundpilot.backend.productcatalog.adapter.api.catalogsync;

import com.fundpilot.backend.productcatalog.application.command.catalogsync.ProductCatalogCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogSynchronizationApi {
    private final ProductCatalogCommandHandler commands;

    public SynchronizationResult synchronize() {
        return new SynchronizationResult(commands.synchronize());
    }

    public record SynchronizationResult(int synchronizedProducts) {}
}
