package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.PendingTransactionCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PendingTransactionCompensationJob {

    private final PendingTransactionCompensationService compensationService;

    @EventListener(ApplicationReadyEvent.class)
    public void compensateOnStartup() {
        compensationService.compensateAll();
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Shanghai")
    public void compensateHourly() {
        compensationService.compensateAll();
    }
}
