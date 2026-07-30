package com.fundpilot.backend.portfolio.adapter.api.fundgrouping;

import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingCommandHandler;
import com.fundpilot.backend.portfolio.application.query.fundgrouping.FundGroupingQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PortfolioGroupingApi {
    private final FundGroupingCommandHandler commands;
    private final FundGroupingQueryHandler queries;

    public void assignByNames(AssignByNames request) {
        commands.assignByNames(request.ownerId(), request.portfolioFundId(), request.names());
    }

    public List<GroupMembership> memberships(long ownerId) {
        return queries.memberships(ownerId).stream()
                .map(value -> new GroupMembership(value.portfolioFundId(), value.groupId(), value.groupName()))
                .toList();
    }

    public record AssignByNames(long ownerId, long portfolioFundId, List<String> names) {
    }

    public record GroupMembership(long portfolioFundId, long groupId, String groupName) {
    }
}
