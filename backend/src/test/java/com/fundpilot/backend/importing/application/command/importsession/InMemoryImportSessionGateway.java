package com.fundpilot.backend.importing.application.command.importsession;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryImportSessionGateway implements ImportSessionGateway {
    private final Map<String, Snapshot> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(Snapshot snapshot) {
        sessions.put(snapshot.id(), snapshot);
    }

    @Override
    public Optional<Snapshot> findOwned(String id, long ownerId) {
        return Optional.ofNullable(sessions.get(id)).filter(snapshot -> snapshot.ownerId() == ownerId);
    }

    @Override
    public List<Snapshot> findVisibleByOwner(long ownerId, Instant terminalCutoff) {
        return sessions.values().stream()
                .filter(snapshot -> snapshot.ownerId() == ownerId)
                .filter(snapshot -> !terminal(snapshot.status()) || !snapshot.updatedAt().isBefore(terminalCutoff))
                .sorted(Comparator.comparing(Snapshot::updatedAt).reversed().thenComparing(Snapshot::id))
                .toList();
    }

    @Override
    public List<Snapshot> findProcessing() {
        return sessions.values().stream().filter(snapshot -> "PROCESSING".equals(snapshot.status())).toList();
    }

    @Override
    public List<Snapshot> findExpiredAwaiting(Instant now) {
        return sessions.values().stream()
                .filter(snapshot -> "WAITING".equals(snapshot.status()) || "CONNECTED".equals(snapshot.status()))
                .filter(snapshot -> snapshot.expiresAt().isBefore(now)).toList();
    }

    @Override
    public void deleteTerminalBefore(Instant cutoff) {
        sessions.values().removeIf(snapshot -> terminal(snapshot.status()) && snapshot.updatedAt().isBefore(cutoff));
    }

    @Override
    public void deleteOwned(String id, long ownerId) {
        sessions.computeIfPresent(id, (key, snapshot) -> snapshot.ownerId() == ownerId ? null : snapshot);
    }

    private static boolean terminal(String status) {
        return "COMPLETED".equals(status) || "EXPIRED".equals(status);
    }
}
