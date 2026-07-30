package com.fundpilot.backend.discipline.application.command.advicelifecycle;

import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounting 交易生命周期投影到 Discipline 建议回应状态。 */
@Service
@RequiredArgsConstructor
public class AdviceLifecycleCommandHandler {
    private final AdviceRepository advice;

    @Transactional
    public void confirmed(long adviceId) {
        advice.findByIdForUpdate(adviceId).ifPresent(value -> {
            value.markResponded();
            advice.save(value);
        });
    }

    @Transactional
    public void cancelled(long adviceId) {
        advice.findByIdForUpdate(adviceId).ifPresent(value -> {
            value.markPending();
            advice.save(value);
        });
    }
}
