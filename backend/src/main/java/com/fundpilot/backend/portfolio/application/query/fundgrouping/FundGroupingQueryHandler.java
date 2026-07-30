package com.fundpilot.backend.portfolio.application.query.fundgrouping;

import com.fundpilot.backend.portfolio.domain.fundgroup.FundGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FundGroupingQueryHandler {
    private final FundGroupRepository groups;

    @Transactional(readOnly = true)
    public List<GroupResult> list(long ownerId) {
        return groups.summarize(ownerId).stream()
                .map(group -> new GroupResult(group.id(), group.name(), group.sortOrder(),
                        group.portfolioFundCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMembershipResult> memberships(long ownerId) {
        return groups.memberships(ownerId).stream()
                .map(group -> new GroupMembershipResult(group.portfolioFundId(), group.groupId(), group.groupName()))
                .toList();
    }

    public record GroupResult(long id, String name, int sortOrder, long portfolioFundCount) {
    }

    public record GroupMembershipResult(long portfolioFundId, long groupId, String groupName) {
    }
}
