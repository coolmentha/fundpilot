package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JdbcImportSessionGateway implements ImportSessionGateway {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final JdbcTemplate jdbc;

    @Override
    public void save(Snapshot snapshot) {
        jdbc.update("""
                INSERT INTO import_session (id, owner_id, status, created_at, updated_at, expires_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (id) DO UPDATE SET
                    owner_id = EXCLUDED.owner_id,
                    status = EXCLUDED.status,
                    created_at = EXCLUDED.created_at,
                    updated_at = EXCLUDED.updated_at,
                    expires_at = EXCLUDED.expires_at,
                    payload = EXCLUDED.payload
                """, snapshot.id(), snapshot.ownerId(), snapshot.status(), snapshot.createdAt().atOffset(ZoneOffset.UTC),
                snapshot.updatedAt().atOffset(ZoneOffset.UTC), snapshot.expiresAt().atOffset(ZoneOffset.UTC), encode(snapshot));
    }

    @Override
    public Optional<Snapshot> findOwned(String id, long ownerId) {
        return query("SELECT payload FROM import_session WHERE id = ? AND owner_id = ?", id, ownerId)
                .stream().findFirst();
    }

    @Override
    public List<Snapshot> findVisibleByOwner(long ownerId, Instant terminalCutoff) {
        return query("""
                SELECT payload FROM import_session
                WHERE owner_id = ?
                  AND (status NOT IN ('COMPLETED', 'EXPIRED') OR updated_at >= ?)
                ORDER BY updated_at DESC, id
                """, ownerId, terminalCutoff.atOffset(ZoneOffset.UTC));
    }

    @Override
    public List<Snapshot> findProcessing() {
        return query("SELECT payload FROM import_session WHERE status = 'PROCESSING' ORDER BY updated_at, id");
    }

    @Override
    public List<Snapshot> findExpiredAwaiting(Instant now) {
        return query("""
                SELECT payload FROM import_session
                WHERE status IN ('WAITING', 'CONNECTED') AND expires_at < ?
                """, now.atOffset(ZoneOffset.UTC));
    }

    @Override
    public void deleteTerminalBefore(Instant cutoff) {
        jdbc.update("""
                DELETE FROM import_session
                WHERE status IN ('COMPLETED', 'EXPIRED') AND updated_at < ?
                """, cutoff.atOffset(ZoneOffset.UTC));
    }

    @Override
    public void deleteOwned(String id, long ownerId) {
        jdbc.update("DELETE FROM import_session WHERE id = ? AND owner_id = ?", id, ownerId);
    }

    private List<Snapshot> query(String sql, Object... arguments) {
        return jdbc.query(sql, (row, index) -> decode(row.getString("payload")), arguments);
    }

    private String encode(Snapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("无法持久化导入会话", exception);
        }
    }

    private Snapshot decode(String payload) {
        try {
            return JSON.readValue(payload, Snapshot.class);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取导入会话", exception);
        }
    }
}
