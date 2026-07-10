package com.fundpilot.backend.fund.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FundNavUpdatedListener {

    private final PendingTransactionCompensationService compensationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFundNavUpdated(FundNavUpdatedEvent event) {
        compensationService.compensateFund(event.fundId());
    }
}
