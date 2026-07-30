package com.fundpilot.backend.accounting.adapter.event.navpublishing;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationCommandHandler;
import com.fundpilot.backend.marketdata.application.event.publishednav.NavPublished;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NavPublishedAccountingListenerTest {
    @Test
    void confirmsPendingTransactionsForThePublishedProductAndBusinessDay() {
        TransactionConfirmationCommandHandler confirmations = mock(TransactionConfirmationCommandHandler.class);
        Instant navDate = Instant.parse("2026-07-24T00:00:00Z");

        new NavPublishedAccountingListener(confirmations).onNavPublished(new NavPublished(
                7L, "001071", navDate, new BigDecimal("1.2345"), new BigDecimal("2.3456"), navDate));

        verify(confirmations).confirmPendingForProduct(7L, navDate);
    }
}
