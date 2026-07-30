package com.fundpilot.backend.accounting.infrastructure.messaging.transactionledger;

import com.fundpilot.backend.accounting.application.event.position.PositionCleared;
import com.fundpilot.backend.accounting.application.event.position.PositionOpened;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCancelled;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCreated;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes Accounting integration events after the handler persists its state. */
@Component
@RequiredArgsConstructor
public class LedgerEventGatewayImpl implements LedgerEventGateway {
    private final ApplicationEventPublisher events;

    @Override
    public void publishCreated(TransactionCreated event) {
        events.publishEvent(event);
    }

    @Override
    public void publishConfirmed(TransactionConfirmed event) {
        events.publishEvent(event);
    }

    @Override
    public void publishCancelled(TransactionCancelled event) {
        events.publishEvent(event);
    }

    @Override
    public void publishPositionOpened(PositionOpened event) {
        events.publishEvent(event);
    }

    @Override
    public void publishPositionCleared(PositionCleared event) {
        events.publishEvent(event);
    }
}
