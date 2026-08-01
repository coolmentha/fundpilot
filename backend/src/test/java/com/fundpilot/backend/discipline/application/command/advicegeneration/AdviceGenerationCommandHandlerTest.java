package com.fundpilot.backend.discipline.application.command.advicegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.application.gateway.advicegeneration.AdviceGenerationFactsGateway;
import com.fundpilot.backend.discipline.domain.advice.Advice;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.advice.AdviceResponseStatus;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.platform.transaction.RequiresNewTransactionExecutor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdviceGenerationCommandHandlerTest {

    private static final Instant BUSINESS_DATE = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void 同日逻辑止损覆盖时不再忽略当日PENDING建议行() {
        AdviceRepository advice = mock(AdviceRepository.class);
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        AdviceGenerationFactsGateway facts = mock(AdviceGenerationFactsGateway.class);
        RequiresNewTransactionExecutor transactions = mock(RequiresNewTransactionExecutor.class);
        when(transactions.execute(any())).thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());

        DisciplineStrategy strategy = strategyTriggeredAt(71L);
        when(strategies.findById(1L)).thenReturn(Optional.of(strategy));

        Advice pendingToday = Advice.rehydrate(71L, 10L, 1L, AdviceAction.SELL, BUSINESS_DATE,
                1, new BigDecimal("0.1"), new BigDecimal("100"), "SHARE", "TRAILING_STOP",
                null, null, AdviceResponseStatus.PENDING);
        when(advice.findByIdForUpdate(71L)).thenReturn(Optional.of(pendingToday));

        when(facts.load(1L, 10L, BUSINESS_DATE)).thenReturn(Optional.of(factsForLogicBroken()));

        Advice generated = Advice.rehydrate(80L, 10L, 1L, AdviceAction.SELL, BUSINESS_DATE,
                null, null, new BigDecimal("100"), "SHARE", "LOGIC_BROKEN", null, null,
                AdviceResponseStatus.PENDING);
        when(advice.replaceGenerated(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(generated);

        new AdviceGenerationCommandHandler(advice, strategies, facts, transactions)
                .generate(1L, BUSINESS_DATE);

        verify(advice, never()).save(pendingToday);
        verify(advice).replaceGenerated(eq(10L), eq(1L), eq(1L), eq(BUSINESS_DATE),
                eq(AdviceAction.SELL), eq(null), eq(null), eq(new BigDecimal("100")),
                eq("SHARE"), eq("LOGIC_BROKEN"), eq(null), eq(null));
        assertThat(pendingToday.responseStatus()).isEqualTo(AdviceResponseStatus.PENDING);
        assertThat(strategy.takeProfitPhase()).isEqualTo("ARMED");
        assertThat(strategy.triggeredAdviceId()).isNull();
    }

    private static DisciplineStrategy strategyTriggeredAt(long adviceId) {
        DisciplineStrategy strategy = DisciplineStrategy.rehydrate(1L, 10L, 1L, "EFFECTIVE",
                new BigDecimal("0.10"), new BigDecimal("0.08"), new BigDecimal("0.10"),
                new BigDecimal("0.20"), new BigDecimal("0.50"), 5, null, null, true,
                "TRIGGERED", null, null, adviceId, null);
        return strategy;
    }

    private static AdviceGenerationFactsGateway.Facts factsForLogicBroken() {
        return new AdviceGenerationFactsGateway.Facts(10L, 1L, 100L, "ACTIVE", "OPEN",
                BUSINESS_DATE, new BigDecimal("1.0"), new BigDecimal("100"),
                new AdviceGenerationFactsGateway.MarketSnapshot(new BigDecimal("0.9"), false, false,
                        "GREEN_EXPANDING", "ACTIVE", new BigDecimal("0.05"), false),
                new BigDecimal("0.9"), new BigDecimal("1.8"), new BigDecimal("2.0"),
                new BigDecimal("2.0"), null, new BigDecimal("100"));
    }
}
