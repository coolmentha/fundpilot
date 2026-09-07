package com.fundpilot.backend.importing.infrastructure.persistence.importitem;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway.ItemRequest;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway.ItemResult;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway.ItemStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportItemReceiptStore {
    private final JdbcTemplate jdbc;

    public Optional<ItemResult> find(ItemRequest request) {
        return jdbc.query("""
                SELECT request_hash, status, message, portfolio_fund_id FROM import_item_receipt
                WHERE owner_id = ? AND session_id = ? AND item_id = ?
                """, (row, index) -> {
            if (!row.getString("request_hash").equals(fingerprint(request))) {
                throw new IllegalArgumentException("同一导入条目不能更改已提交的选择");
            }
            return new ItemResult(ItemStatus.valueOf(row.getString("status")), row.getString("message"),
                    row.getLong("portfolio_fund_id"));
        }, request.ownerId(), request.sessionId(), request.itemId()).stream().findFirst();
    }

    public void lock(ItemRequest request) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        // Receipt identity precedes fund identity; a duplicate request and concurrent imports serialize.
        lock("import-item:" + request.ownerId() + ":" + request.sessionId() + ":" + request.itemId());
        lock("import-product:" + request.fundCode().trim());
    }

    private void lock(String key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", key);
    }

    public void save(ItemRequest request, ItemResult result) {
        jdbc.update("""
                INSERT INTO import_item_receipt
                (owner_id, session_id, item_id, request_hash, status, message, portfolio_fund_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, request.ownerId(), request.sessionId(), request.itemId(), fingerprint(request),
                result.status().name(), result.message(), result.portfolioFundId());
    }

    private static String fingerprint(ItemRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : new Object[] {request.fundCode(), request.fundName(), request.shares(),
                    request.costPerShare(), request.mode()}) {
                add(digest, String.valueOf(value));
            }
            request.groupNames().forEach(value -> add(digest, value));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
