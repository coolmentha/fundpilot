package com.fundpilot.backend.accounting.infrastructure.gateway.fundonboarding;

import com.fundpilot.backend.accounting.application.gateway.fundonboarding.FundGroupingGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundGroupingGatewayImpl implements FundGroupingGateway {
    private final PortfolioGroupingApi groups;

    @Override
    public void assignByNames(long ownerId, long portfolioFundId, List<String> names) {
        try {
            groups.assignByNames(new PortfolioGroupingApi.AssignByNames(ownerId, portfolioFundId,
                    names == null ? List.of() : names));
        } catch (PortfolioGroupingApi.Failure failure) {
            throw new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }
    }
}
