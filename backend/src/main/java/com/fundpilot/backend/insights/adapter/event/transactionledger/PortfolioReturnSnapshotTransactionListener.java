package com.fundpilot.backend.insights.adapter.event.transactionledger;

import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.insights.application.command.portfolioreturn.PortfolioReturnSnapshotCommandHandler;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortfolioReturnSnapshotTransactionListener {
    private final PortfolioReturnSnapshotCommandHandler snapshots;

    @ApplicationModuleListener
    public void onConfirmed(TransactionConfirmed event) {
        Instant tradeDate = event.tradeDate() == null ? event.confirmedAt() : event.tradeDate();
        snapshots.recaptureExistingFrom(event.ownerId(), BusinessDay.toDateLabel(tradeDate));
    }
}
