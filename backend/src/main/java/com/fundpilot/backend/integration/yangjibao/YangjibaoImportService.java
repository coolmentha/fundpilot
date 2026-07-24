package com.fundpilot.backend.integration.yangjibao;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.fund.service.FundTransactionService;
import com.fundpilot.backend.user.service.CurrentUserService;
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

@Service
@RequiredArgsConstructor
public class YangjibaoImportService {
    private final YangjibaoClient client;
    private final FundRepository fundRepository;
    private final FundPositionService positionService;
    private final FundService fundService;
    private final FundTransactionService transactionService;
    private final CurrentUserService currentUserService;
    private final TaskExecutor applicationTaskExecutor;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    @Value("${fundpilot.yangjibao.session-ttl:PT30M}") private Duration ttl;

    public SessionView create() {
        var qr = client.createQrCode();
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(currentUserService.userId(), qr.id(), qr.url(), Instant.now().plus(ttl)));
        return new SessionView(id, "WAITING", qr.url(), null);
    }

    public SessionView state(String id) {
        Session session = require(id);
        synchronized (session) {
            if (session.token == null) {
                var remote = client.qrState(session.qrId);
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

    public ImportJobView startImport(String id, List<YangjibaoImportController.Selection> selections) {
        Session session = require(id);
        synchronized (session) {
            if (session.job != null) return session.job.view();
            List<PreviewItem> preview = preview(id);
            Map<String, PreviewItem> byId = new HashMap<>();
            preview.forEach(item -> byId.put(item.itemId(), item));
            Set<String> codes = new HashSet<>();
            List<YangjibaoImportController.Selection> selected = selections == null ? List.of() : List.copyOf(selections);
            for (var selection : selected) {
                PreviewItem item = byId.get(selection.itemId());
                if (item == null || !codes.add(item.fundCode())) throw invalid("选择项无效或同一基金代码选择了多份");
            }
            session.job = new ImportJob(selected);
            applicationTaskExecutor.execute(() -> currentUserService.runAs(session.ownerId,
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
            List<YangjibaoImportController.Selection> retry = session.job.selections.stream()
                    .filter(selection -> failedIds.contains(selection.itemId())).toList();
            Map<String, PreviewItem> byId = new HashMap<>();
            session.preview.forEach(item -> byId.put(item.itemId(), item));
            session.job = new ImportJob(retry);
            applicationTaskExecutor.execute(() -> currentUserService.runAs(session.ownerId,
                    () -> process(id, session, byId, retry)));
            return session.job.view();
        }
    }

    private void process(String id, Session session, Map<String, PreviewItem> byId,
                         List<YangjibaoImportController.Selection> selections) {
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

    private ImportResult importOne(PreviewItem item, YangjibaoImportController.ExistingMode mode) {
        Optional<FundEntity> existing = findOwnedFund(item.fundCode());
        if (existing.isEmpty()) {
            fundService.create(new FundCreateRequest(item.fundCode(), item.fundName(), null, null, null,
                    null, null, item.yangjibaoShares(), item.costPerShare(), null, List.of(item.accountName())));
            return new ImportResult(item.itemId(), item.fundCode(), "CREATED", "已新增基金");
        }
        if (mode == null) {
            throw new BusinessException(ErrorCode.YANGJIBAO_IMPORT_INVALID, "请选择已有基金的处理方式");
        }
        if (mode != YangjibaoImportController.ExistingMode.SYNC_TARGET) {
            return new ImportResult(item.itemId(), item.fundCode(), "SKIPPED", "以本系统份额为准");
        }
        var adjusted = transactionService.adjustToHoldingShares(existing.get().getId(), item.yangjibaoShares());
        return new ImportResult(item.itemId(), item.fundCode(), adjusted.transaction() == null ? "SKIPPED" : "ADJUSTED",
                adjusted.transaction() == null ? "份额一致" : "已按目标份额调整");
    }

    private List<PreviewItem> loadPreview(String token) {
        List<PreviewItem> items = new ArrayList<>();
        for (var account : client.accounts(token)) for (var holding : client.holdings(token, account.id())) {
            FundEntity local = findOwnedFund(holding.code()).orElse(null);
            BigDecimal localShares = local == null ? BigDecimal.ZERO : positionService.getHoldingShares(local.getId());
            items.add(new PreviewItem(account.id() + ":" + holding.id(), account.id(), account.title(), holding.code(),
                    holding.short_name(), holding.hold_share(), holding.hold_cost(), local == null ? null : local.getId(), localShares));
        }
        return List.copyOf(items);
    }

    private Session require(String id) {
        Session session = sessions.get(id);
        if (session == null || session.ownerId != currentUserService.userId()) {
            throw new BusinessException(ErrorCode.YANGJIBAO_SESSION_NOT_FOUND, "导入会话不存在");
        }
        if (Instant.now().isAfter(session.expiresAt)) { expire(id, session); throw invalid("导入会话已过期"); }
        return session;
    }
    private Optional<FundEntity> findOwnedFund(String fundCode) {
        long userId = currentUserService.userId();
        return userId == 0L ? fundRepository.findByFundCode(fundCode)
                : fundRepository.findByFundCodeAndOwnerId(fundCode, userId);
    }
    private void expire(String id, Session session) { session.token = null; session.status = "EXPIRED"; sessions.remove(id, session); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.YANGJIBAO_SESSION_INVALID, message); }

    public record SessionView(String sessionId, String status, String qrUrl, Instant expiresAt) {}
    public record PreviewItem(String itemId, String accountId, String accountName, String fundCode, String fundName,
                              BigDecimal yangjibaoShares, BigDecimal costPerShare, Long localFundId, BigDecimal localShares) {}
    public record ImportResult(String itemId, String fundCode, String status, String message) {}
    public record ImportJobView(ImportStatus status, int total, int processed, int succeeded, int failed,
                                String currentFund, List<ImportResult> results) {}
    public enum ImportStatus {PROCESSING, COMPLETED}

    private static class ImportJob {
        final List<YangjibaoImportController.Selection> selections;
        final List<ImportResult> results = new ArrayList<>();
        ImportStatus status = ImportStatus.PROCESSING;
        int processed;
        String currentFund;

        ImportJob(List<YangjibaoImportController.Selection> selections) { this.selections = selections; }

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
