package com.fundpilot.backend.importing.application.gateway.importsession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ImportedHoldingGateway {
    Optional<LocalHolding> find(long ownerId, String fundCode);
    ImportedHolding create(long ownerId, String fundCode, String fundName, BigDecimal shares,
                           BigDecimal costPerShare, List<String> groupNames);
    boolean synchronize(long ownerId, long portfolioFundId, BigDecimal targetShares);

    record LocalHolding(long portfolioFundId, Long legacyFundId, BigDecimal shares) {}
    record ImportedHolding(long portfolioFundId, Long legacyFundId) {}
}
