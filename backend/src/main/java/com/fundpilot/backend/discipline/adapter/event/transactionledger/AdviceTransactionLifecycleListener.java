package com.fundpilot.backend.discipline.adapter.event.transactionledger;

import com.fundpilot.backend.accounting.application.event.transaction.TransactionCancelled;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.discipline.application.command.advicelifecycle.AdviceLifecycleCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 提交后独立消费 Accounting 生命周期事件；重复投递只重放幂等状态。 */
@Component
@RequiredArgsConstructor
public class AdviceTransactionLifecycleListener {
    private final AdviceLifecycleCommandHandler lifecycle;

    @ApplicationModuleListener
    public void onConfirmed(TransactionConfirmed event) {
        if (event.disciplineAdviceId() != null) {
            lifecycle.confirmed(event.disciplineAdviceId());
        }
    }

    @ApplicationModuleListener
    public void onCancelled(TransactionCancelled event) {
        if (event.disciplineAdviceId() != null) {
            lifecycle.cancelled(event.disciplineAdviceId());
        }
    }
}
