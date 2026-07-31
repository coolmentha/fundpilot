package com.fundpilot.backend.discipline.application.command.adviceresponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.application.gateway.adviceresponse.AdviceTransactionGateway;
import com.fundpilot.backend.discipline.domain.advice.Advice;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.advice.AdviceResponseStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdviceResponseCommandHandlerTest {

    @Test
    void accept_卖出建议仅请求Accounting创建待确认账目() {
        AdviceRepository advice = mock(AdviceRepository.class);
        AdviceTransactionGateway transactions = mock(AdviceTransactionGateway.class);
        AdviceResponseCommandHandler handler = new AdviceResponseCommandHandler(advice, transactions,
                Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));
        when(advice.findByIdForUpdate(71L)).thenReturn(Optional.of(
                Advice.rehydrate(71L, 11L, 3L, AdviceAction.SELL, null, AdviceResponseStatus.PENDING)));
        when(transactions.createPending(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AdviceTransactionGateway.PendingTransaction(101L));

        var result = handler.accept(3L, 71L, null, new BigDecimal("50"), null);

        assertThat(result.transactionId()).isEqualTo(101L);
        ArgumentCaptor<AdviceTransactionGateway.CreatePending> request =
                ArgumentCaptor.forClass(AdviceTransactionGateway.CreatePending.class);
        verify(transactions).createPending(request.capture());
        assertThat(request.getValue().source()).isEqualTo(AdviceTransactionGateway.Source.DECREASE);
        assertThat(request.getValue().adviceId()).isEqualTo(71L);
    }

    @Test
    void accept_逻辑止损拒绝非全仓份额() {
        AdviceRepository advice = mock(AdviceRepository.class);
        AdviceTransactionGateway transactions = mock(AdviceTransactionGateway.class);
        AdviceResponseCommandHandler handler = new AdviceResponseCommandHandler(advice, transactions,
                Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));
        when(advice.findByIdForUpdate(71L)).thenReturn(Optional.of(Advice.rehydrate(71L, 11L, 3L,
                AdviceAction.SELL, null, null, null, new BigDecimal("100"), "SHARE", "LOGIC_BROKEN", null,
                null, AdviceResponseStatus.PENDING)));
        when(transactions.confirmedHoldingShares(3L, 11L)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> handler.accept(3L, 71L, null, new BigDecimal("50"), null))
                .isInstanceOf(AdviceResponseFailure.class)
                .extracting(error -> ((AdviceResponseFailure) error).code())
                .isEqualTo(AdviceResponseFailure.Code.VALUE_NOT_ALLOWED);

        verify(transactions, never()).createPending(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void accept_逻辑止损全仓份额创建待确认账目() {
        AdviceRepository advice = mock(AdviceRepository.class);
        AdviceTransactionGateway transactions = mock(AdviceTransactionGateway.class);
        AdviceResponseCommandHandler handler = new AdviceResponseCommandHandler(advice, transactions,
                Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));
        when(advice.findByIdForUpdate(71L)).thenReturn(Optional.of(Advice.rehydrate(71L, 11L, 3L,
                AdviceAction.SELL, null, null, null, new BigDecimal("100"), "SHARE", "LOGIC_BROKEN", null,
                null, AdviceResponseStatus.PENDING)));
        when(transactions.confirmedHoldingShares(3L, 11L)).thenReturn(new BigDecimal("100"));
        when(transactions.createPending(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AdviceTransactionGateway.PendingTransaction(101L));

        var result = handler.accept(3L, 71L, null, new BigDecimal("100"), null);

        assertThat(result.transactionId()).isEqualTo(101L);
    }
}
