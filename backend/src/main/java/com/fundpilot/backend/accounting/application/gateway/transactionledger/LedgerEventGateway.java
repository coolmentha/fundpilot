package com.fundpilot.backend.accounting.application.gateway.transactionledger;

import com.fundpilot.backend.accounting.application.event.position.PositionCleared;
import com.fundpilot.backend.accounting.application.event.position.PositionOpened;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCancelled;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCreated;

/** 账目集成事件的出站契约；投递实现放在 {@code infrastructure.messaging}。 */
public interface LedgerEventGateway {

    void publishCreated(TransactionCreated event);

    void publishConfirmed(TransactionConfirmed event);

    void publishCancelled(TransactionCancelled event);

    void publishPositionOpened(PositionOpened event);

    void publishPositionCleared(PositionCleared event);
}
