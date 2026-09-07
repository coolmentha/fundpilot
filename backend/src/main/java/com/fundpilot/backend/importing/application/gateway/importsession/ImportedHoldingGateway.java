package com.fundpilot.backend.importing.application.gateway.importsession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ImportedHoldingGateway {
    ItemResult importItem(ItemRequest request);
    Optional<LocalHolding> find(long ownerId, String fundCode);
    ImportedHolding create(long ownerId, String fundCode, String fundName, BigDecimal shares,
                           BigDecimal costPerShare, List<String> groupNames);
    boolean synchronize(long ownerId, long portfolioFundId, BigDecimal targetShares);

    record LocalHolding(long portfolioFundId, Long legacyFundId, BigDecimal shares) {}
    record ImportedHolding(long portfolioFundId, Long legacyFundId) {}
    record ItemRequest(long ownerId, String sessionId, String itemId, String fundCode, String fundName,
                       BigDecimal shares, BigDecimal costPerShare, List<String> groupNames, ExistingMode mode) {
        public ItemRequest { groupNames = List.copyOf(groupNames); }
    }
    enum ExistingMode { KEEP_LOCAL, SYNC_TARGET }
    enum ItemStatus { CREATED, SKIPPED, ADJUSTED }
    record ItemResult(ItemStatus status, String message, long portfolioFundId) {}
}
