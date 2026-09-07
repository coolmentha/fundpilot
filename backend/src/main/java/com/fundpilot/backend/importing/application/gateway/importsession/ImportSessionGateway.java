package com.fundpilot.backend.importing.application.gateway.importsession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImportSessionGateway {
    void save(Snapshot snapshot);

    Optional<Snapshot> findOwned(String id, long ownerId);

    List<Snapshot> findVisibleByOwner(long ownerId, Instant terminalCutoff);

    List<Snapshot> findProcessing();

    List<Snapshot> findExpiredAwaiting(Instant now);

    void deleteTerminalBefore(Instant cutoff);

    void deleteOwned(String id, long ownerId);

    record Snapshot(String id, long ownerId, String qrId, String qrUrl, String token, String status,
                    Instant createdAt, Instant updatedAt, Instant expiresAt, List<StoredPreview> preview,
                    List<StoredSelection> selections, List<StoredResult> results, String currentFund) {
        public Snapshot {
            preview = List.copyOf(preview);
            selections = List.copyOf(selections);
            results = List.copyOf(results);
        }
    }

    record StoredPreview(String itemId, String accountId, String accountName, String fundCode, String fundName,
                         BigDecimal yangjibaoShares, BigDecimal costPerShare, Long localFundId,
                         BigDecimal localShares) {
    }

    record StoredSelection(StoredPreview item, String existingMode) {
    }

    record StoredResult(String itemId, String fundCode, String status, String failureCode, String message,
                        String correlationId) {
        public StoredResult(String itemId, String fundCode, String status, String message) {
            this(itemId, fundCode, status, null, message, null);
        }
    }
}
