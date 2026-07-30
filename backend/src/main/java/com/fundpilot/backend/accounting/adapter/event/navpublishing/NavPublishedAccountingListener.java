package com.fundpilot.backend.accounting.adapter.event.navpublishing;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationCommandHandler;
import com.fundpilot.backend.marketdata.application.event.publishednav.NavPublished;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Confirms Accounting transactions only after MarketData has committed the published NAV. */
@Component
@RequiredArgsConstructor
public class NavPublishedAccountingListener {
    private final TransactionConfirmationCommandHandler confirmations;

    @ApplicationModuleListener
    public void onNavPublished(NavPublished event) {
        confirmations.confirmPendingForProduct(event.fundProductId(), event.navDate());
    }
}
