package com.fundpilot.backend.portfolio.domain.fundgroup;

import java.util.List;

public interface FundGroupRepository {
    List<FundGroup> findByOwnerId(long ownerId);

    List<FundGroup> replace(long ownerId, List<FundGroup> groups);

    List<GroupSummary> summarize(long ownerId);

    void assignByNames(long ownerId, long portfolioFundId, Long legacyFundId, List<String> names);

    record GroupSummary(long id, String name, int sortOrder, long portfolioFundCount) {
    }
}
