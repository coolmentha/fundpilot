package com.fundpilot.backend.productcatalog.adapter.scheduler.catalogsync;

import com.fundpilot.backend.platform.observability.JobName;
import com.fundpilot.backend.productcatalog.application.command.catalogsync.ProductCatalogCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCatalogSynchronizationJob {
    private final ProductCatalogCommandHandler commands;

    @JobName("产品目录同步")
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void synchronizeDaily() {
        commands.synchronize();
    }
}
