package com.fundpilot.backend.discipline.application.command.advicelifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.discipline.domain.strategy.TakeProfitPhase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdviceLifecycleCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void confirmed_止盈建议确认后策略进入冷静期并记录开始时间() {
        AdviceRepository advice = mock(AdviceRepository.class);
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        DisciplineStrategy strategy = triggeredStrategy(71L);
        when(strategies.findByTriggeredAdviceId(71L)).thenReturn(Optional.of(strategy));

        new AdviceLifecycleCommandHandler(advice, strategies, Clock.fixed(NOW, ZoneOffset.UTC))
                .confirmed(71L);

        assertThat(strategy.takeProfitPhase()).isEqualTo(TakeProfitPhase.COOLDOWN);
        assertThat(strategy.cooldownStartedAt()).isEqualTo(NOW);
        assertThat(strategy.triggeredAdviceId()).isNull();
        verify(strategies).save(strategy);
    }

    @Test
    void confirmed_非止盈触发的建议确认不影响策略状态() {
        AdviceRepository advice = mock(AdviceRepository.class);
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        DisciplineStrategy strategy = triggeredStrategy(72L);
        strategy.supersedeTriggered();
        when(strategies.findByTriggeredAdviceId(71L)).thenReturn(Optional.of(strategy));

        new AdviceLifecycleCommandHandler(advice, strategies, Clock.fixed(NOW, ZoneOffset.UTC))
                .confirmed(71L);

        assertThat(strategy.takeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
    }

    @Test
    void cancelled_撤单释放TRIGGERED策略回到ARMED() {
        AdviceRepository advice = mock(AdviceRepository.class);
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        DisciplineStrategy strategy = triggeredStrategy(73L);
        when(strategies.findByTriggeredAdviceId(73L)).thenReturn(Optional.of(strategy));

        new AdviceLifecycleCommandHandler(advice, strategies, Clock.fixed(NOW, ZoneOffset.UTC))
                .cancelled(73L);

        assertThat(strategy.takeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(strategy.triggeredAdviceId()).isNull();
        verify(strategies).save(strategy);
    }

    private static DisciplineStrategy triggeredStrategy(long adviceId) {
        DisciplineStrategy strategy = DisciplineStrategy.create(10L, 1L, new DisciplineStrategy.Input(
                new BigDecimal("0.10"), new BigDecimal("0.08"), new BigDecimal("0.10"),
                new BigDecimal("0.20"), new BigDecimal("0.50"), 5));
        strategy.activate();
        strategy.markTriggered(adviceId);
        return strategy;
    }
}
