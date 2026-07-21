package com.fundpilot.backend.integration.yangjibao;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.fund.service.FundTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    @Value("${fundpilot.yangjibao.session-ttl:PT30M}") private Duration ttl;

    public SessionView create() {
        var qr = client.createQrCode();
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(qr.id(), qr.url(), Instant.now().plus(ttl)));
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

    public List<ImportResult> run(String id, List<YangjibaoImportController.Selection> selections) {
        Session session = require(id);
        synchronized (session) {
            if (session.results != null) return session.results;
            List<PreviewItem> preview = preview(id);
            Map<String, PreviewItem> byId = new HashMap<>();
            preview.forEach(item -> byId.put(item.itemId(), item));
            Set<String> codes = new HashSet<>();
            for (var selection : selections == null ? List.<YangjibaoImportController.Selection>of() : selections) {
                PreviewItem item = byId.get(selection.itemId());
                if (item == null || !codes.add(item.fundCode())) throw invalid("选择项无效或同一基金代码选择了多份");
            }
            List<ImportResult> results = new ArrayList<>();
            for (var selection : selections == null ? List.<YangjibaoImportController.Selection>of() : selections) {
                PreviewItem item = byId.get(selection.itemId());
                try { results.add(importOne(item, selection.existingMode())); }
                catch (Exception e) { results.add(new ImportResult(item.itemId(), item.fundCode(), "FAILED", e.getMessage())); }
            }
            session.results = List.copyOf(results);
            session.token = null;
            session.status = "COMPLETED";
            return session.results;
        }
    }

    public void cancel(String id) { Session session = sessions.remove(id); if (session != null) session.token = null; }

    private ImportResult importOne(PreviewItem item, YangjibaoImportController.ExistingMode mode) {
        Optional<FundEntity> existing = fundRepository.findByFundCode(item.fundCode());
        if (existing.isEmpty()) {
            fundService.create(new FundCreateRequest(item.fundCode(), item.fundName(), null, null, null,
                    null, null, item.yangjibaoShares(), item.costPerShare(), null, null));
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
            FundEntity local = fundRepository.findByFundCode(holding.code()).orElse(null);
            BigDecimal localShares = local == null ? BigDecimal.ZERO : positionService.getHoldingShares(local.getId());
            items.add(new PreviewItem(account.id() + ":" + holding.id(), account.id(), account.title(), holding.code(),
                    holding.short_name(), holding.hold_share(), holding.hold_cost(), local == null ? null : local.getId(), localShares));
        }
        return List.copyOf(items);
    }

    private Session require(String id) {
        Session session = sessions.get(id);
        if (session == null) throw new BusinessException(ErrorCode.YANGJIBAO_SESSION_NOT_FOUND, "导入会话不存在");
        if (Instant.now().isAfter(session.expiresAt)) { expire(id, session); throw invalid("导入会话已过期"); }
        return session;
    }
    private void expire(String id, Session session) { session.token = null; session.status = "EXPIRED"; sessions.remove(id); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.YANGJIBAO_SESSION_INVALID, message); }

    public record SessionView(String sessionId, String status, String qrUrl, Instant expiresAt) {}
    public record PreviewItem(String itemId, String accountId, String accountName, String fundCode, String fundName,
                              BigDecimal yangjibaoShares, BigDecimal costPerShare, Long localFundId, BigDecimal localShares) {}
    public record ImportResult(String itemId, String fundCode, String status, String message) {}
    private static class Session {
        final String qrId, qrUrl; final Instant expiresAt; String token; String status = "WAITING";
        List<PreviewItem> preview; List<ImportResult> results;
        Session(String qrId, String qrUrl, Instant expiresAt) { this.qrId = qrId; this.qrUrl = qrUrl; this.expiresAt = expiresAt; }
    }
}
