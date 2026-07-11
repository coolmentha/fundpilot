package com.fundpilot.backend.fund.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Flyway 完成后同步执行一次性账本重建；异常向上传播以阻止半完成账本启动。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AccountingRebuildRunner implements ApplicationRunner {

    private final AccountingRebuildService accountingRebuildService;

    @Override
    public void run(ApplicationArguments args) {
        accountingRebuildService.rebuildIfPending();
    }
}
