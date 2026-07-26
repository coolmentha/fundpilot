package com.fundpilot.backend.portfolio.adapter.api.fundgrouping;

import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PortfolioGroupingApi {
    private final FundGroupingCommandHandler commands;

    public void assignByNames(AssignByNames request) {
        commands.assignByNames(request.ownerId(), request.portfolioFundId(), request.names());
    }

    public record AssignByNames(long ownerId, long portfolioFundId, List<String> names) {
    }
}
