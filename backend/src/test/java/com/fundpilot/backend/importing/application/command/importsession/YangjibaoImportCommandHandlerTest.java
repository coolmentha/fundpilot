package com.fundpilot.backend.importing.application.command.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class YangjibaoImportCommandHandlerTest {
    @Mock YangjibaoSourceGateway source;
    @Mock ImportedHoldingGateway holdings;
    @Mock ImportActorGateway actors;
    YangjibaoImportCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(actors.currentOwnerId()).thenReturn(1L);
        lenient().doAnswer(invocation -> { invocation.getArgument(1, Runnable.class).run(); return null; })
                .when(actors).runAsOwner(eq(1L), any(Runnable.class));
        handler = new YangjibaoImportCommandHandler(source, holdings, actors, Runnable::run);
        ReflectionTestUtils.setField(handler, "ttl", Duration.ofMinutes(30));
    }

    @Test
    void importsSelectedNewFundFromServerPreviewSnapshot() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());

        String id = handler.create().sessionId();
        handler.state(id);
        var item = handler.preview(id).getFirst();
        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(), null)));

        assertThat(handler.importStatus(id).results().getFirst().status()).isEqualTo("CREATED");
        verify(holdings).create(1L, "017093", "示例基金", new BigDecimal("100.00"),
                new BigDecimal("1.23"), List.of("支付宝"));
    }

    @Test
    void rejectsSelectingTwoAccountsForSameFundCode() {
        when(source.createQrCode()).thenReturn(new YangjibaoSourceGateway.QrCode("qr", "https://qr"));
        when(source.qrState("qr")).thenReturn(new YangjibaoSourceGateway.QrState("2", "token"));
        when(source.accounts("token")).thenReturn(List.of(
                new YangjibaoSourceGateway.Account("a", "A"), new YangjibaoSourceGateway.Account("b", "B")));
        when(source.holdings(eq("token"), anyString())).thenReturn(List.of(
                new YangjibaoSourceGateway.Holding("h", "017093", "示例基金", BigDecimal.TEN, BigDecimal.ONE)));
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        String id = handler.create().sessionId(); handler.state(id); var preview = handler.preview(id);

        assertThatThrownBy(() -> handler.startImport(id, preview.stream()
                .map(item -> new YangjibaoImportCommandHandler.Selection(item.itemId(), null)).toList()))
                .isInstanceOf(YangjibaoImportFailure.class);
        verify(holdings, never()).create(eq(1L), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void retryDoesNotRepeatSuccessfulItems() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.create(eq(1L), eq("017093"), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenReturn(new ImportedHoldingGateway.ImportedHolding(9L, 19L));
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();
        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(), null)));

        assertThat(handler.importStatus(id).failed()).isEqualTo(1);
        assertThat(handler.retryFailed(id).failed()).isZero();
        verify(holdings, times(2)).create(eq(1L), eq("017093"), anyString(), any(), any(), any());
    }

    @Test
    void repeatedSubmissionDoesNotCreateHoldingTwice() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();
        var selection = new YangjibaoImportCommandHandler.Selection(item.itemId(), null);

        var first = handler.startImport(id, List.of(selection));
        var repeated = handler.startImport(id, List.of(selection));

        assertThat(repeated).isEqualTo(first);
        verify(holdings, times(1)).create(eq(1L), eq("017093"), anyString(), any(), any(), any());
    }

    @Test
    void existingHoldingRequiresModeAndSynchronizesOnlyWhenRequested() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.of(
                new ImportedHoldingGateway.LocalHolding(9L, 19L, BigDecimal.TEN)));
        when(holdings.synchronize(1L, 9L, new BigDecimal("100.00"))).thenReturn(true);
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();

        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(),
                YangjibaoImportCommandHandler.ExistingMode.SYNC_TARGET)));

        assertThat(handler.importStatus(id).results().getFirst().status()).isEqualTo("ADJUSTED");
        verify(holdings).synchronize(1L, 9L, new BigDecimal("100.00"));
    }

    @Test
    void retryRetainsPreviouslyCompletedResults() {
        connectedHolding();
        when(source.holdings("token", "a")).thenReturn(List.of(
                new YangjibaoSourceGateway.Holding("h", "017093", "成功基金", BigDecimal.TEN, BigDecimal.ONE),
                new YangjibaoSourceGateway.Holding("h2", "017094", "重试基金", BigDecimal.TEN, BigDecimal.ONE)));
        when(holdings.create(eq(1L), eq("017093"), anyString(), any(), any(), any()))
                .thenReturn(new ImportedHoldingGateway.ImportedHolding(9L, 19L));
        when(holdings.create(eq(1L), eq("017094"), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenReturn(new ImportedHoldingGateway.ImportedHolding(10L, 20L));
        String id = handler.create().sessionId();
        handler.state(id);
        var preview = handler.preview(id);
        handler.startImport(id, preview.stream().map(item ->
                new YangjibaoImportCommandHandler.Selection(item.itemId(), null)).toList());
        assertThat(handler.importStatus(id).succeeded()).isEqualTo(1);

        var retried = handler.retryFailed(id);

        assertThat(retried.total()).isEqualTo(2);
        assertThat(retried.succeeded()).isEqualTo(2);
        assertThat(retried.results()).extracting(YangjibaoImportCommandHandler.ImportResult::itemId)
                .containsExactlyInAnyOrder("a:h", "a:h2");
        verify(holdings, times(1)).create(eq(1L), eq("017093"), anyString(), any(), any(), any());
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        when(source.createQrCode()).thenReturn(new YangjibaoSourceGateway.QrCode("qr", "https://qr"));
        String id = handler.create().sessionId();
        when(actors.currentOwnerId()).thenReturn(2L);

        assertThatThrownBy(() -> handler.state(id)).isInstanceOf(YangjibaoImportFailure.class);
        verify(source, never()).qrState(anyString());
    }

    @Test
    void proactivelyPurgesExpiredAbandonedSessions() {
        when(source.createQrCode()).thenReturn(new YangjibaoSourceGateway.QrCode("qr", "https://qr"));
        ReflectionTestUtils.setField(handler, "ttl", Duration.ofSeconds(-1));
        String id = handler.create().sessionId();
        handler.purgeExpiredSessions();
        assertThatThrownBy(() -> handler.state(id)).isInstanceOf(YangjibaoImportFailure.class);
    }

    @Test
    void stateAfterCompleted_不再轮询二维码且不删除会话() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();
        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(), null)));
        assertThat(handler.importStatus(id).status())
                .isEqualTo(YangjibaoImportCommandHandler.ImportStatus.COMPLETED);

        var view = handler.state(id);

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(source, times(1)).qrState("qr");
        assertThatThrownBy(() -> handler.preview(id)).isInstanceOf(YangjibaoImportFailure.class);
    }

    private void connectedHolding() {
        when(source.createQrCode()).thenReturn(new YangjibaoSourceGateway.QrCode("qr", "https://qr"));
        when(source.qrState("qr")).thenReturn(new YangjibaoSourceGateway.QrState("2", "token"));
        when(source.accounts("token")).thenReturn(List.of(new YangjibaoSourceGateway.Account("a", "支付宝")));
        when(source.holdings("token", "a")).thenReturn(List.of(new YangjibaoSourceGateway.Holding(
                "h", "017093", "示例基金", new BigDecimal("100.00"), new BigDecimal("1.23"))));
    }
}
