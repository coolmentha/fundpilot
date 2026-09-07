package com.fundpilot.backend.importing.application.command.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class YangjibaoImportCommandHandlerTest {
    @Mock YangjibaoSourceGateway source;
    @Mock ImportedHoldingGateway holdings;
    @Mock ImportActorGateway actors;
    ImportSessionGateway sessions;
    YangjibaoImportCommandHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(actors.currentOwnerId()).thenReturn(1L);
        lenient().doAnswer(invocation -> { invocation.getArgument(1, Runnable.class).run(); return null; })
                .when(actors).runAsOwner(eq(1L), any(Runnable.class));
        sessions = new InMemoryImportSessionGateway();
        handler = new YangjibaoImportCommandHandler(source, holdings, actors, Runnable::run, sessions);
        ReflectionTestUtils.setField(handler, "ttl", Duration.ofMinutes(30));
    }

    @Test
    void importsSelectedNewFundFromServerPreviewSnapshot() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any())).thenReturn(new ImportedHoldingGateway.ItemResult(
                ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 9L));

        String id = handler.create().sessionId();
        handler.state(id);
        var item = handler.preview(id).getFirst();
        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(), null)));

        assertThat(handler.importStatus(id).results().getFirst().status()).isEqualTo("CREATED");
        verify(holdings).importItem(new ImportedHoldingGateway.ItemRequest(1L, id, item.itemId(),
                "017093", "示例基金", new BigDecimal("100.00"), new BigDecimal("1.23"),
                List.of("支付宝"), null));
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
        verify(holdings, never()).importItem(any());
    }

    @Test
    void retryDoesNotRepeatSuccessfulItems() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any()))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenReturn(new ImportedHoldingGateway.ItemResult(
                        ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 9L));
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();
        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(), null)));

        assertThat(handler.importStatus(id).failed()).isEqualTo(1);
        assertThat(handler.retryFailed(id).failed()).isZero();
        verify(holdings, times(2)).importItem(any());
    }

    @Test
    void repeatedSubmissionDoesNotCreateHoldingTwice() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any())).thenReturn(new ImportedHoldingGateway.ItemResult(
                ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 9L));
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();
        var selection = new YangjibaoImportCommandHandler.Selection(item.itemId(), null);

        var first = handler.startImport(id, List.of(selection));
        var repeated = handler.startImport(id, List.of(selection));

        assertThat(first.status()).isEqualTo(YangjibaoImportCommandHandler.ImportStatus.PROCESSING);
        assertThat(repeated.status()).isEqualTo(YangjibaoImportCommandHandler.ImportStatus.COMPLETED);
        verify(holdings, times(1)).importItem(any());
    }

    @Test
    void existingHoldingRequiresModeAndSynchronizesOnlyWhenRequested() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.of(
                new ImportedHoldingGateway.LocalHolding(9L, 19L, BigDecimal.TEN)));
        when(holdings.importItem(any())).thenReturn(new ImportedHoldingGateway.ItemResult(
                ImportedHoldingGateway.ItemStatus.ADJUSTED, "已按目标份额调整", 9L));
        String id = handler.create().sessionId(); handler.state(id); var item = handler.preview(id).getFirst();

        handler.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(),
                YangjibaoImportCommandHandler.ExistingMode.SYNC_TARGET)));

        assertThat(handler.importStatus(id).results().getFirst().status()).isEqualTo("ADJUSTED");
        verify(holdings).importItem(argThat(request -> request.ownerId() == 1L
                && request.sessionId().equals(id)
                && request.itemId().equals(item.itemId())
                && request.mode() == ImportedHoldingGateway.ExistingMode.SYNC_TARGET));
    }

    @Test
    void retryRetainsPreviouslyCompletedResults() {
        connectedHolding();
        when(source.holdings("token", "a")).thenReturn(List.of(
                new YangjibaoSourceGateway.Holding("h", "017093", "成功基金", BigDecimal.TEN, BigDecimal.ONE),
                new YangjibaoSourceGateway.Holding("h2", "017094", "重试基金", BigDecimal.TEN, BigDecimal.ONE)));
        when(holdings.importItem(argThat(request -> request != null && "017093".equals(request.fundCode()))))
                .thenReturn(new ImportedHoldingGateway.ItemResult(
                        ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 9L));
        when(holdings.importItem(argThat(request -> request != null && "017094".equals(request.fundCode()))))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenReturn(new ImportedHoldingGateway.ItemResult(
                        ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 10L));
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
        verify(holdings, times(1)).importItem(argThat(request -> request != null
                && "017093".equals(request.fundCode())));
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
    void validationModeDoesNotQueryOrScheduleProcessingTasksOnStartup() {
        ImportSessionGateway persistentSessions = mock(ImportSessionGateway.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        var validationHandler = new YangjibaoImportCommandHandler(
                source, holdings, actors, taskExecutor, persistentSessions);
        ReflectionTestUtils.setField(validationHandler, "deploymentValidationMode", true);

        validationHandler.resumeOnStartup();

        verifyNoInteractions(persistentSessions, taskExecutor);
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
