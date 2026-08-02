package com.fundpilot.backend.discipline.application.command.advicelifecycle;

import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounting 交易生命周期投影到 Discipline 建议回应状态与止盈冷静期。 */
@Service
@RequiredArgsConstructor
public class AdviceLifecycleCommandHandler {
    private final AdviceRepository advice;
    private final DisciplineStrategyRepository strategies;
    private final Clock clock;

    @Transactional
    public void confirmed(long adviceId) {
        advice.findByIdForUpdate(adviceId).ifPresent(value -> {
            value.markResponded();
            advice.save(value);
        });
        strategies.findByTriggeredAdviceId(adviceId).ifPresent(strategy -> {
            strategy.enterCooldown(clock.instant());
            strategies.save(strategy);
        });
    }

    @Transactional
    public void cancelled(long adviceId) {
        advice.findByIdForUpdate(adviceId).ifPresent(value -> {
            value.markPending();
            advice.save(value);
        });
        strategies.findByTriggeredAdviceId(adviceId).ifPresent(strategy -> {
            strategy.supersedeTriggered();
            strategies.save(strategy);
        });
    }
}
