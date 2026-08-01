package com.fundpilot.backend.importing.application.command.importsession;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
@RequiredArgsConstructor
public class YangjibaoImportCommandHandler {
    private final YangjibaoSourceGateway source;
    private final ImportedHoldingGateway holdings;
    private final ImportActorGateway actors;
    private final TaskExecutor applicationTaskExecutor;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    @Value("${fundpilot.yangjibao.session-ttl:PT30M}") private Duration ttl;

    public SessionView create() {
        var qr = remote(source::createQrCode);
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(actors.currentOwnerId(), qr.id(), qr.url(), Instant.now().plus(ttl)));
        return new SessionView(id, "WAITING", qr.url(), null);
    }

    public SessionView state(String id) {
        Session session = require(id);
        synchronized (session) {
            // 已进入导入流程(job 非空,含 COMPLETED)后不再轮询上游二维码:token 已被置空,
            // 重新轮询可能因远端失效 expire() 删除会话,导致导入结果与重试能力丢失
            if (session.token == null && session.job == null) {
                var remote = remote(() -> source.qrState(session.qrId));
                if ("2".equals(remote.state()) && remote.token() != null) {
                    session.token = remote.token();
                    session.status = "CONNECTED";
                } else if ("3".equals(remote.state())) {
                    expire(id, session);
                }
            }
            return new SessionView(id, session.status, session.qrUrl, session.expiresAt);
        }
    }

    public List<PreviewItem> preview(String id) {
        Session session = require(id);
        synchronized (session) {
            if (session.token == null) state(id);
            if (session.token == null) throw invalid("二维码尚未扫码成功");
            if (session.preview == null) session.preview = loadPreview(session.token);
            return session.preview;
        }
    }

    public ImportJobView startImport(String id, List<Selection> selections) {
        Session session = require(id);
        synchronized (session) {
            if (session.job != null) return session.job.view();
            List<PreviewItem> preview = preview(id);
            Map<String, PreviewItem> byId = new HashMap<>();
            preview.forEach(item -> byId.put(item.itemId(), item));
            Set<String> codes = new HashSet<>();
            List<Selection> selected = selections == null ? List.of() : List.copyOf(selections);
            for (var selection : selected) {
                PreviewItem item = byId.get(selection.itemId());
                if (item == null || !codes.add(item.fundCode())) throw invalid("选择项无效或同一基金代码选择了多份");
            }
            session.job = new ImportJob(selected);
            applicationTaskExecutor.execute(() -> actors.runAsOwner(session.ownerId,
                    () -> process(id, session, byId, selected)));
            return session.job.view();
        }
    }

    public ImportJobView importStatus(String id) {
        Session session = require(id);
        synchronized (session) {
            if (session.job == null) throw invalid("导入任务尚未开始");
            return session.job.view();
        }
    }

    public ImportJobView retryFailed(String id) {
        Session session = require(id);
        synchronized (session) {
            if (session.job == null || session.job.status != ImportStatus.COMPLETED) throw invalid("导入任务尚未完成");
            Set<String> failedIds = new HashSet<>();
            session.job.results.stream().filter(result -> "FAILED".equals(result.status())).forEach(result -> failedIds.add(result.itemId()));
            if (failedIds.isEmpty()) throw invalid("没有可重试的失败项");
            List<Selection> retry = session.job.selections.stream()
                    .filter(selection -> failedIds.contains(selection.itemId())).toList();
            Map<String, PreviewItem> byId = new HashMap<>();
            session.preview.forEach(item -> byId.put(item.itemId(), item));
            session.job = new ImportJob(retry);
            applicationTaskExecutor.execute(() -> actors.runAsOwner(session.ownerId,
                    () -> process(id, session, byId, retry)));
            return session.job.view();
        }
    }

    private void process(String id, Session session, Map<String, PreviewItem> byId,
                         List<Selection> selections) {
        for (var selection : selections) {
            if (sessions.get(id) != session) return;
            PreviewItem item = byId.get(selection.itemId());
            ImportResult result;
            try { result = importOne(item, selection.existingMode()); }
            catch (Exception e) { result = new ImportResult(item.itemId(), item.fundCode(), "FAILED", e.getMessage()); }
            synchronized (session) {
                session.job.results.add(result);
                session.job.processed++;
                session.job.currentFund = item.fundCode();
            }
        }
        synchronized (session) {
            session.job.status = ImportStatus.COMPLETED;
            session.job.currentFund = null;
            session.token = null;
            session.status = "COMPLETED";
        }
    }

    public void cancel(String id) {
        Session session = require(id);
        sessions.remove(id, session);
        session.token = null;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    void purgeExpiredSessions() {
        Instant now = Instant.now();
        sessions.forEach((id, session) -> {
            if (now.isAfter(session.expiresAt)) {
                synchronized (session) {
                    if (now.isAfter(session.expiresAt)) expire(id, session);
                }
            }
        });
    }

    private ImportResult importOne(PreviewItem item, ExistingMode mode) {
        Optional<ImportedHoldingGateway.LocalHolding> existing = holdings.find(
                actors.currentOwnerId(), item.fundCode());
        if (existing.isEmpty()) {
            holdings.create(actors.currentOwnerId(), item.fundCode(), item.fundName(), item.yangjibaoShares(),
                    item.costPerShare(), List.of(item.accountName()));
            return new ImportResult(item.itemId(), item.fundCode(), "CREATED", "已新增基金");
        }
        if (mode == null) {
            throw new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_INVALID,
                    "请选择已有基金的处理方式");
        }
        if (mode != ExistingMode.SYNC_TARGET) {
            return new ImportResult(item.itemId(), item.fundCode(), "SKIPPED", "以本系统份额为准");
        }
        boolean adjusted = holdings.synchronize(actors.currentOwnerId(), existing.get().portfolioFundId(),
                item.yangjibaoShares());
        return new ImportResult(item.itemId(), item.fundCode(), adjusted ? "ADJUSTED" : "SKIPPED",
                adjusted ? "已按目标份额调整" : "份额一致");
    }

    private List<PreviewItem> loadPreview(String token) {
        List<PreviewItem> items = new ArrayList<>();
        long ownerId = actors.currentOwnerId();
        for (var account : remote(() -> source.accounts(token)))
            for (var holding : remote(() -> source.holdings(token, account.id()))) {
            ImportedHoldingGateway.LocalHolding local = holdings.find(ownerId, holding.code()).orElse(null);
            BigDecimal localShares = local == null ? BigDecimal.ZERO : local.shares();
            items.add(new PreviewItem(account.id() + ":" + holding.id(), account.id(), account.title(), holding.code(),
                    holding.name(), holding.shares(), holding.costPerShare(),
                    local == null ? null : local.legacyFundId(), localShares));
        }
        return List.copyOf(items);
    }

    private Session require(String id) {
        Session session = sessions.get(id);
        if (session == null || session.ownerId != actors.currentOwnerId()) {
            throw new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_SESSION_NOT_FOUND,
                    "导入会话不存在");
        }
        if (Instant.now().isAfter(session.expiresAt)) { expire(id, session); throw invalid("导入会话已过期"); }
        return session;
    }
    private void expire(String id, Session session) { session.token = null; session.status = "EXPIRED"; sessions.remove(id, session); }
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

    public record SessionView(String sessionId, String status, String qrUrl, Instant expiresAt) {}
    public record PreviewItem(String itemId, String accountId, String accountName, String fundCode, String fundName,
                              BigDecimal yangjibaoShares, BigDecimal costPerShare, Long localFundId, BigDecimal localShares) {}
    public record ImportResult(String itemId, String fundCode, String status, String message) {}
    public record ImportJobView(ImportStatus status, int total, int processed, int succeeded, int failed,
                                String currentFund, List<ImportResult> results) {}
    public enum ImportStatus {PROCESSING, COMPLETED}
    public enum ExistingMode {KEEP_LOCAL, SYNC_TARGET}
    public record Selection(String itemId, ExistingMode existingMode) {}

    private static class ImportJob {
        final List<Selection> selections;
        final List<ImportResult> results = new ArrayList<>();
        ImportStatus status = ImportStatus.PROCESSING;
        int processed;
        String currentFund;

        ImportJob(List<Selection> selections) { this.selections = selections; }

        ImportJobView view() {
            int failed = (int) results.stream().filter(result -> "FAILED".equals(result.status())).count();
            return new ImportJobView(status, selections.size(), processed, processed - failed, failed,
                    currentFund, List.copyOf(results));
        }
    }

    private static class Session {
        final long ownerId; final String qrId, qrUrl; final Instant expiresAt; String token; String status = "WAITING";
        List<PreviewItem> preview; ImportJob job;
        Session(long ownerId, String qrId, String qrUrl, Instant expiresAt) {
            this.ownerId = ownerId; this.qrId = qrId; this.qrUrl = qrUrl; this.expiresAt = expiresAt;
        }
    }
}
