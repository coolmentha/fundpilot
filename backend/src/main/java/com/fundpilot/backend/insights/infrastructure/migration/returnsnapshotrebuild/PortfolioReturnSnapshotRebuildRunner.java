package com.fundpilot.backend.insights.infrastructure.migration.returnsnapshotrebuild;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Flyway 完成后执行一次性组合收益快照重算。必须晚于 AccountingRebuildRunner:
 * 历史成本按账本重放得出,而账本本身要先被单位净值口径重建过。
 */
@Component
@ConditionalOnProperty(name = "fundpilot.deployment.validation-mode", havingValue = "false", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class PortfolioReturnSnapshotRebuildRunner implements ApplicationRunner {

    private final PortfolioReturnSnapshotRebuildService rebuildService;

    @Override
    public void run(ApplicationArguments args) {
        rebuildService.rebuildIfPending();
    }
}
