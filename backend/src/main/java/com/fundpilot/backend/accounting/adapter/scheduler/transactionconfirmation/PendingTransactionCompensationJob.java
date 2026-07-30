package com.fundpilot.backend.accounting.adapter.scheduler.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PendingTransactionCompensationJob {

    private final TransactionCompensationCommandHandler accountingCompensation;

    @Value("${fundpilot.deployment.validation-mode:false}")
    private boolean deploymentValidationMode;

    @EventListener(ApplicationReadyEvent.class)
    public void compensateOnStartup() {
        if (deploymentValidationMode) {
            return;
        }
        accountingCompensation.compensateAll(java.time.Instant.now());
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Shanghai")
    public void compensateHourly() {
        accountingCompensation.compensateAll(java.time.Instant.now());
    }
}
