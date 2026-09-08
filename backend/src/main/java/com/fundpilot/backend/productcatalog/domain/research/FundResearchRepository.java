package com.fundpilot.backend.productcatalog.domain.research;

import java.util.List;
import java.util.Optional;

public interface FundResearchRepository {
    Optional<FundResearch> findByFundCode(String fundCode);
    List<String> findTrackedFundCodes(int limit);
    FundResearch save(FundResearch research);
}
