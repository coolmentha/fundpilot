package com.fundpilot.backend.importing.application.command.importsession;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway.Snapshot;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway.StoredPreview;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway.StoredResult;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway.StoredSelection;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class YangjibaoImportCommandHandler {
    private static final String WAITING = "WAITING";
    private static final String CONNECTED = "CONNECTED";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final String EXPIRED = "EXPIRED";

    private final YangjibaoSourceGateway source;
    private final ImportedHoldingGateway holdings;
    private final ImportActorGateway actors;
    private final TaskExecutor applicationTaskExecutor;
    private final ImportSessionGateway sessions;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    @Value("${fundpilot.yangjibao.session-ttl:PT30M}") private Duration ttl = Duration.ofMinutes(30);
    @Value("${fundpilot.yangjibao.session-retention:P7D}") private Duration retention = Duration.ofDays(7);
    @Value("${fundpilot.deployment.validation-mode:false}") private boolean deploymentValidationMode;

    public SessionView create() {
        var qr = remote(source::createQrCode);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        sessions.save(new Snapshot(id, actors.currentOwnerId(), qr.id(), qr.url(), null, WAITING,
                now, now, now.plus(ttl), List.of(), List.of(), List.of(), null));
        return new SessionView(id, WAITING, qr.url(), now.plus(ttl));
    }

    public List<ImportSessionSummary> listSessions() {
        purgeExpiredSessions();
        Instant cutoff = Instant.now().minus(retention);
        return sessions.findVisibleByOwner(actors.currentOwnerId(), cutoff).stream()
                .map(this::summary).toList();
    }

    public SessionView state(String id) {
        synchronized (lock(id)) {
            Snapshot session = require(id);
            if (WAITING.equals(session.status()) && session.token() == null) {
                String qrId = session.qrId();
                var remote = remote(() -> source.qrState(qrId));
                if ("2".equals(remote.state()) && remote.token() != null) {
                    session = copy(session, remote.token(), CONNECTED, Instant.now(), session.preview(),
                            session.selections(), session.results(), session.currentFund());
                    sessions.save(session);
                } else if ("3".equals(remote.state())) {
                    session = expire(session, Instant.now());
                }
            }
            return new SessionView(id, session.status(), session.qrUrl(),
                    terminalOrProcessing(session.status()) ? null : session.expiresAt());
        }
    }

    public List<PreviewItem> preview(String id) {
        synchronized (lock(id)) {
            Snapshot session = require(id);
            if (terminalOrProcessing(session.status())) throw invalid("导入流程已开始或结束");
            if (session.token() == null) {
                state(id);
                session = require(id);
            }
            if (session.token() == null) throw invalid("二维码尚未扫码成功");
            if (session.preview().isEmpty()) {
                List<StoredPreview> preview = loadPreview(session.ownerId(), session.token()).stream()
                        .map(this::store).toList();
                session = copy(session, session.token(), session.status(), Instant.now(), preview,
                        session.selections(), session.results(), session.currentFund());
                sessions.save(session);
            }
            return session.preview().stream().map(this::view).toList();
        }
    }

    public ImportJobView startImport(String id, List<Selection> selections) {
        Snapshot started;
        synchronized (lock(id)) {
            Snapshot session = require(id);
            if (terminalOrProcessing(session.status())) return jobView(session);
            List<PreviewItem> preview = preview(id);
            session = require(id);
            Map<String, PreviewItem> byId = new HashMap<>();
            preview.forEach(item -> byId.put(item.itemId(), item));
            Set<String> codes = new HashSet<>();
            List<Selection> requested = selections == null ? List.of() : List.copyOf(selections);
            List<StoredSelection> selected = new ArrayList<>();
            for (var selection : requested) {
                PreviewItem item = byId.get(selection.itemId());
                if (item == null || !codes.add(item.fundCode())) {
                    throw invalid("选择项无效或同一基金代码选择了多份");
                }
                selected.add(new StoredSelection(store(item),
                        selection.existingMode() == null ? null : selection.existingMode().name()));
            }
            started = copy(session, null, PROCESSING, Instant.now(), session.preview(), selected, List.of(), null);
            sessions.save(started);
        }
        ImportJobView view = jobView(started);
        schedule(started);
        return view;
    }

    public ImportJobView importStatus(String id) {
        Snapshot session;
        ImportJobView view;
        synchronized (lock(id)) {
            session = require(id);
            if (!terminalOrProcessing(session.status())) throw invalid("导入任务尚未开始");
            view = jobView(session);
        }
        if (PROCESSING.equals(session.status())) schedule(session);
        return view;
    }

    public ImportJobView retryFailed(String id) {
        Snapshot retrying;
        synchronized (lock(id)) {
            Snapshot session = require(id);
            if (!COMPLETED.equals(session.status())) throw invalid("导入任务尚未完成");
            Set<String> failedIds = new HashSet<>();
            session.results().stream().filter(result -> "FAILED".equals(result.status()))
                    .forEach(result -> failedIds.add(result.itemId()));
            if (failedIds.isEmpty()) throw invalid("没有可重试的失败项");
            List<StoredResult> retained = session.results().stream()
                    .filter(result -> !failedIds.contains(result.itemId())).toList();
            retrying = copy(session, null, PROCESSING, Instant.now(), session.preview(),
                    session.selections(), retained, null);
            sessions.save(retrying);
        }
        schedule(retrying);
        synchronized (lock(id)) {
            return jobView(require(id));
        }
    }

    private void schedule(Snapshot session) {
        if (!running.add(session.id())) return;
        try {
            // ponytail: process-local claim assumes one service instance; add a DB lease only when multi-instance import is required.
            applicationTaskExecutor.execute(() -> {
                try {
                    actors.runAsOwner(session.ownerId(), () -> process(session.id(), session.ownerId()));
                } finally {
                    running.remove(session.id());
                }
            });
        } catch (RuntimeException | Error failure) {
            running.remove(session.id());
            throw failure;
        }
    }

    private void process(String id, long ownerId) {
        Snapshot initial = sessions.findOwned(id, ownerId).orElse(null);
        if (initial == null || !PROCESSING.equals(initial.status())) return;
        for (var selection : initial.selections()) {
            synchronized (lock(id)) {
                Snapshot current = sessions.findOwned(id, ownerId).orElse(null);
                if (current == null || !PROCESSING.equals(current.status())) return;
                if (hasResult(current, selection.item().itemId())) continue;
                sessions.save(copy(current, null, PROCESSING, Instant.now(), current.preview(),
                        current.selections(), current.results(), selection.item().fundCode()));
            }

            StoredResult result;
            try {
                result = importOne(id, ownerId, selection);
            } catch (Exception exception) {
                result = failedResult(id, selection, exception);
            }

            synchronized (lock(id)) {
                Snapshot current = sessions.findOwned(id, ownerId).orElse(null);
                if (current == null || !PROCESSING.equals(current.status())) return;
                if (hasResult(current, result.itemId())) continue;
                List<StoredResult> results = new ArrayList<>(current.results());
                results.add(result);
                sessions.save(copy(current, null, PROCESSING, Instant.now(), current.preview(),
                        current.selections(), results, selection.item().fundCode()));
            }
        }
        synchronized (lock(id)) {
            Snapshot current = sessions.findOwned(id, ownerId).orElse(null);
            if (current != null && PROCESSING.equals(current.status())) {
                sessions.save(copy(current, null, COMPLETED, Instant.now(), current.preview(),
                        current.selections(), current.results(), null));
            }
        }
    }

    public void cancel(String id) {
        synchronized (lock(id)) {
            Snapshot session = require(id);
            sessions.deleteOwned(id, session.ownerId());
        }
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    void purgeExpiredSessions() {
        Instant now = Instant.now();
        sessions.findExpiredAwaiting(now).forEach(session -> {
            synchronized (lock(session.id())) {
                sessions.findOwned(session.id(), session.ownerId())
                        .filter(current -> WAITING.equals(current.status()) || CONNECTED.equals(current.status()))
                        .filter(current -> current.expiresAt().isBefore(now))
                        .ifPresent(current -> expire(current, now));
            }
        });
        sessions.deleteTerminalBefore(now.minus(retention));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOnStartup() {
        if (!deploymentValidationMode) resumeProcessingTasks();
    }

    void resumeProcessingTasks() {
        sessions.findProcessing().forEach(session -> {
            try {
                schedule(session);
            } catch (RuntimeException exception) {
                log.warn("恢复导入任务失败 sessionId={}", session.id(), exception);
            }
        });
    }

    private StoredResult importOne(String sessionId, long ownerId, StoredSelection selection) {
        StoredPreview item = selection.item();
        var result = holdings.importItem(new ImportedHoldingGateway.ItemRequest(ownerId, sessionId, item.itemId(),
                item.fundCode(), item.fundName(), item.yangjibaoShares(), item.costPerShare(),
                List.of(item.accountName()), selection.existingMode() == null ? null
                        : ImportedHoldingGateway.ExistingMode.valueOf(selection.existingMode())));
        return new StoredResult(item.itemId(), item.fundCode(), result.status().name(), result.message());
    }

    private StoredResult failedResult(String sessionId, StoredSelection selection, Exception exception) {
        String failureCode;
        String message;
        if (exception instanceof IllegalArgumentException
                || hasCode(exception, YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_VALIDATION_FAILED)) {
            failureCode = "IMPORT_VALIDATION_FAILED";
            message = "导入数据校验失败，请检查后重试";
        } else if (hasCode(exception, YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_CONFLICT)) {
            failureCode = "IMPORT_CONFLICT";
            message = "导入冲突，请选择有效的处理方式";
        } else if (hasCode(exception, YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_DEPENDENCY_FAILED)) {
            failureCode = "IMPORT_DEPENDENCY_FAILED";
            message = "暂时无法完成导入，请稍后重试";
        } else {
            failureCode = "IMPORT_INTERNAL_FAILED";
            message = "导入失败，请联系支持并提供关联标识";
        }
        String correlationId = UUID.randomUUID().toString();
        log.error("导入条目失败 sessionId={} itemId={} fundCode={} failureCode={} correlationId={} exceptionType={}",
                sessionId, selection.item().itemId(), selection.item().fundCode(), failureCode, correlationId,
                exception.getClass().getName());
        return new StoredResult(selection.item().itemId(), selection.item().fundCode(), "FAILED", failureCode,
                message, correlationId);
    }

    private static boolean hasCode(Exception exception, YangjibaoImportFailure.Code code) {
        return exception instanceof YangjibaoImportFailure failure && failure.code() == code;
    }

    private List<PreviewItem> loadPreview(long ownerId, String token) {
        List<PreviewItem> items = new ArrayList<>();
        for (var account : remote(() -> source.accounts(token))) {
            for (var holding : remote(() -> source.holdings(token, account.id()))) {
                ImportedHoldingGateway.LocalHolding local = holdings.find(ownerId, holding.code()).orElse(null);
                BigDecimal localShares = local == null ? BigDecimal.ZERO : local.shares();
                items.add(new PreviewItem(account.id() + ":" + holding.id(), account.id(), account.title(),
                        holding.code(), holding.name(), holding.shares(), holding.costPerShare(),
                        local == null ? null : local.legacyFundId(), localShares));
            }
        }
        return List.copyOf(items);
    }

    private Snapshot require(String id) {
        long ownerId = actors.currentOwnerId();
        Snapshot session = sessions.findOwned(id, ownerId).orElseThrow(this::notFound);
        Instant now = Instant.now();
        if (terminal(session.status()) && session.updatedAt().isBefore(now.minus(retention))) {
            sessions.deleteOwned(id, ownerId);
            throw notFound();
        }
        if (EXPIRED.equals(session.status())) throw invalid("导入会话已过期");
        if ((WAITING.equals(session.status()) || CONNECTED.equals(session.status()))
                && session.expiresAt().isBefore(now)) {
            expire(session, now);
            throw invalid("导入会话已过期");
        }
        return session;
    }

    private Snapshot expire(Snapshot session, Instant now) {
        Snapshot expired = copy(session, null, EXPIRED, now, session.preview(), session.selections(),
                session.results(), null);
        sessions.save(expired);
        return expired;
    }

    private YangjibaoImportFailure notFound() {
        return new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_SESSION_NOT_FOUND,
                "导入会话不存在");
    }

    private YangjibaoImportFailure invalid(String message) {
        return new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_SESSION_INVALID, message);
    }

    private static <T> T remote(Supplier<T> call) {
        try {
            return call.get();
        } catch (YangjibaoSourceGateway.Failure failure) {
            throw new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_API_FAILED,
                    "养基宝接口调用失败");
        }
    }

    private ImportSessionSummary summary(Snapshot session) {
        ImportJobView job = jobView(session);
        return new ImportSessionSummary(session.id(), session.status(), session.createdAt(), session.updatedAt(),
                terminalOrProcessing(session.status()) ? null : session.expiresAt(), job.total(), job.processed(),
                job.succeeded(), job.failed());
    }

    private ImportJobView jobView(Snapshot session) {
        int failed = (int) session.results().stream().filter(result -> "FAILED".equals(result.status())).count();
        ImportStatus status = COMPLETED.equals(session.status()) ? ImportStatus.COMPLETED : ImportStatus.PROCESSING;
        return new ImportJobView(status, session.selections().size(), session.results().size(),
                session.results().size() - failed, failed, session.currentFund(),
                session.results().stream().map(this::view).toList());
    }

    private static boolean hasResult(Snapshot session, String itemId) {
        return session.results().stream().anyMatch(result -> result.itemId().equals(itemId));
    }

    private Object lock(String id) {
        return locks.computeIfAbsent(id, ignored -> new Object());
    }

    private Snapshot copy(Snapshot session, String token, String status, Instant updatedAt,
                          List<StoredPreview> preview, List<StoredSelection> selections,
                          List<StoredResult> results, String currentFund) {
        return new Snapshot(session.id(), session.ownerId(), session.qrId(), session.qrUrl(), token, status,
                session.createdAt(), updatedAt, session.expiresAt(), preview, selections, results, currentFund);
    }

    private StoredPreview store(PreviewItem item) {
        return new StoredPreview(item.itemId(), item.accountId(), item.accountName(), item.fundCode(), item.fundName(),
                item.yangjibaoShares(), item.costPerShare(), item.localFundId(), item.localShares());
    }

    private PreviewItem view(StoredPreview item) {
        return new PreviewItem(item.itemId(), item.accountId(), item.accountName(), item.fundCode(), item.fundName(),
                item.yangjibaoShares(), item.costPerShare(), item.localFundId(), item.localShares());
    }

    private ImportResult view(StoredResult result) {
        return new ImportResult(result.itemId(), result.fundCode(), result.status(), result.failureCode(),
                result.message(), result.correlationId());
    }

    private static boolean terminal(String status) {
        return COMPLETED.equals(status) || EXPIRED.equals(status);
    }

    private static boolean terminalOrProcessing(String status) {
        return terminal(status) || PROCESSING.equals(status);
    }

    public record SessionView(String sessionId, String status, String qrUrl, Instant expiresAt) {
    }

    public record ImportSessionSummary(String sessionId, String status, Instant createdAt, Instant updatedAt,
                                       Instant expiresAt, int total, int processed, int succeeded, int failed) {
    }

    public record PreviewItem(String itemId, String accountId, String accountName, String fundCode, String fundName,
                              BigDecimal yangjibaoShares, BigDecimal costPerShare, Long localFundId,
                              BigDecimal localShares) {
    }

    public record ImportResult(String itemId, String fundCode, String status, String failureCode, String message,
                               String correlationId) {
    }

    public record ImportJobView(ImportStatus status, int total, int processed, int succeeded, int failed,
                                String currentFund, List<ImportResult> results) {
    }

    public enum ImportStatus {PROCESSING, COMPLETED}

    public enum ExistingMode {KEEP_LOCAL, SYNC_TARGET}

    public record Selection(String itemId, ExistingMode existingMode) {
    }
}
