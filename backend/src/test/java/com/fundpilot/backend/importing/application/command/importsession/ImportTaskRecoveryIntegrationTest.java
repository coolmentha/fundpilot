package com.fundpilot.backend.importing.application.command.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import com.fundpilot.backend.importing.infrastructure.gateway.importsession.JdbcImportSessionGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = FundPilotBackendApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/fundpilot?currentSchema=fundpilot_import_002",
        "spring.flyway.schemas=fundpilot_import_002",
        "spring.flyway.default-schema=fundpilot_import_002",
        "spring.jpa.properties.hibernate.default_schema=fundpilot_import_002",
        "fundpilot.deployment.validation-mode=true"
})
class ImportTaskRecoveryIntegrationTest {
    @Autowired JdbcImportSessionGateway sessions;
    @Autowired JdbcTemplate jdbc;
    YangjibaoSourceGateway source;
    ImportedHoldingGateway holdings;
    ImportActorGateway actors;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM import_session");
        source = mock(YangjibaoSourceGateway.class);
        holdings = mock(ImportedHoldingGateway.class);
        actors = mock(ImportActorGateway.class);
        when(actors.currentOwnerId()).thenReturn(11L);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(actors).runAsOwner(anyLong(), any(Runnable.class));
    }

    @Test
    void processingTaskSurvivesHandlerRecreationAndResumesRemainingItems() {
        connectedHoldings();
        when(holdings.find(anyLong(), any())).thenReturn(Optional.empty());
        when(holdings.importItem(any()))
                .thenReturn(result(ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 91L))
                .thenThrow(new AssertionError("simulated process stop"))
                .thenReturn(result(ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 92L));
        var queued = new QueueExecutor();
        var first = handler(queued);
        String id = first.create().sessionId();
        first.state(id);
        var preview = first.preview(id);

        var started = first.startImport(id, preview.stream()
                .map(item -> new YangjibaoImportCommandHandler.Selection(item.itemId(), null)).toList());

        assertThat(started.status()).isEqualTo(YangjibaoImportCommandHandler.ImportStatus.PROCESSING);
        assertThat(started.processed()).isZero();
        assertThatThrownBy(queued::runNext).isInstanceOf(AssertionError.class);
        assertThat(sessions.findOwned(id, 11L).orElseThrow().results()).hasSize(1);

        var restarted = handler(Runnable::run);
        restarted.resumeOnStartup();

        var recovered = restarted.importStatus(id);
        assertThat(recovered.status()).isEqualTo(YangjibaoImportCommandHandler.ImportStatus.COMPLETED);
        assertThat(recovered.results()).extracting(YangjibaoImportCommandHandler.ImportResult::fundCode)
                .containsExactly("017093", "017094");
        verify(holdings, times(1)).importItem(argThat(request -> "017093".equals(request.fundCode())));
        verify(holdings, times(2)).importItem(argThat(request -> "017094".equals(request.fundCode())));

        when(actors.currentOwnerId()).thenReturn(22L);
        assertThatThrownBy(() -> restarted.importStatus(id)).isInstanceOf(YangjibaoImportFailure.class);
    }

    @Test
    void failedItemRetrySurvivesHandlerRecreationAndKeepsCompletedItems() {
        connectedHoldings();
        when(holdings.find(anyLong(), any())).thenReturn(Optional.empty());
        when(holdings.importItem(argThat(request -> request != null && "017093".equals(request.fundCode()))))
                .thenReturn(result(ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 91L));
        when(holdings.importItem(argThat(request -> request != null && "017094".equals(request.fundCode()))))
                .thenThrow(new RuntimeException("temporary dependency failure"))
                .thenReturn(result(ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 92L));
        var first = handler(Runnable::run);
        String id = first.create().sessionId();
        first.state(id);
        var preview = first.preview(id);
        first.startImport(id, preview.stream()
                .map(item -> new YangjibaoImportCommandHandler.Selection(item.itemId(), null)).toList());
        assertThat(first.importStatus(id).failed()).isEqualTo(1);

        var restarted = handler(Runnable::run);
        var retried = restarted.retryFailed(id);

        assertThat(retried).extracting(
                YangjibaoImportCommandHandler.ImportJobView::total,
                YangjibaoImportCommandHandler.ImportJobView::processed,
                YangjibaoImportCommandHandler.ImportJobView::succeeded,
                YangjibaoImportCommandHandler.ImportJobView::failed)
                .containsExactly(2, 2, 2, 0);
        verify(holdings, times(1)).importItem(argThat(request -> request != null
                && "017093".equals(request.fundCode())));
        verify(holdings, times(2)).importItem(argThat(request -> request != null
                && "017094".equals(request.fundCode())));
        assertThatThrownBy(() -> restarted.retryFailed(id)).isInstanceOf(YangjibaoImportFailure.class);
        verify(holdings, times(3)).importItem(any());
    }

    @Test
    void listKeepsOldActiveTaskButFiltersOldTerminalAndReportsCounts() {
        Instant now = Instant.now();
        sessions.save(snapshot("active", 11L, "PROCESSING", now.minus(Duration.ofDays(30)),
                List.of(selection("active-1", "017091"), selection("active-2", "017092")),
                List.of(storedResult("active-1", "017091", "CREATED"))));
        sessions.save(snapshot("recent", 11L, "COMPLETED", now.minus(Duration.ofHours(1)),
                List.of(selection("recent-1", "017093"), selection("recent-2", "017094")),
                List.of(storedResult("recent-1", "017093", "CREATED"),
                        storedResult("recent-2", "017094", "FAILED"))));
        sessions.save(snapshot("old-terminal", 11L, "COMPLETED", now.minus(Duration.ofDays(8)),
                List.of(selection("old-1", "017095")), List.of(storedResult("old-1", "017095", "CREATED"))));
        sessions.save(snapshot("other-owner", 22L, "PROCESSING", now,
                List.of(selection("other-1", "017096")), List.of()));
        var handler = handler(Runnable::run);

        var listed = handler.listSessions();

        assertThat(listed).extracting(YangjibaoImportCommandHandler.ImportSessionSummary::sessionId)
                .containsExactly("recent", "active");
        assertThat(listed.getFirst()).extracting(
                YangjibaoImportCommandHandler.ImportSessionSummary::total,
                YangjibaoImportCommandHandler.ImportSessionSummary::processed,
                YangjibaoImportCommandHandler.ImportSessionSummary::succeeded,
                YangjibaoImportCommandHandler.ImportSessionSummary::failed)
                .containsExactly(2, 2, 1, 1);
        assertThat(sessions.findOwned("active", 11L)).isPresent();
        assertThat(sessions.findOwned("old-terminal", 11L)).isEmpty();
    }

    private YangjibaoImportCommandHandler handler(TaskExecutor executor) {
        var handler = new YangjibaoImportCommandHandler(source, holdings, actors, executor, sessions);
        ReflectionTestUtils.setField(handler, "ttl", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(handler, "retention", Duration.ofDays(7));
        return handler;
    }

    private void connectedHoldings() {
        when(source.createQrCode()).thenReturn(new YangjibaoSourceGateway.QrCode("qr", "https://qr"));
        when(source.qrState("qr")).thenReturn(new YangjibaoSourceGateway.QrState("2", "token"));
        when(source.accounts("token")).thenReturn(List.of(new YangjibaoSourceGateway.Account("a", "支付宝")));
        when(source.holdings("token", "a")).thenReturn(List.of(
                new YangjibaoSourceGateway.Holding("h1", "017093", "示例一", BigDecimal.TEN, BigDecimal.ONE),
                new YangjibaoSourceGateway.Holding("h2", "017094", "示例二", BigDecimal.TEN, BigDecimal.ONE)));
    }

    private static ImportedHoldingGateway.ItemResult result(
            ImportedHoldingGateway.ItemStatus status, String message, long portfolioFundId) {
        return new ImportedHoldingGateway.ItemResult(status, message, portfolioFundId);
    }

    private static ImportSessionGateway.Snapshot snapshot(String id, long ownerId, String status, Instant updatedAt,
                                                           List<ImportSessionGateway.StoredSelection> selections,
                                                           List<ImportSessionGateway.StoredResult> results) {
        return new ImportSessionGateway.Snapshot(id, ownerId, "qr-" + id, "https://qr/" + id, null, status,
                updatedAt.minusSeconds(60), updatedAt, updatedAt.plusSeconds(1800), List.of(), selections, results, null);
    }

    private static ImportSessionGateway.StoredSelection selection(String itemId, String fundCode) {
        return new ImportSessionGateway.StoredSelection(new ImportSessionGateway.StoredPreview(itemId, "a", "支付宝",
                fundCode, "示例基金", BigDecimal.TEN, BigDecimal.ONE, null, BigDecimal.ZERO), null);
    }

    private static ImportSessionGateway.StoredResult storedResult(String itemId, String fundCode, String status) {
        return new ImportSessionGateway.StoredResult(itemId, fundCode, status,
                "FAILED".equals(status) ? "暂时无法完成导入" : "已新增基金");
    }

    private static final class QueueExecutor implements TaskExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runNext() {
            tasks.remove().run();
        }
    }
}
