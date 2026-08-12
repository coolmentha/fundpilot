package com.fundpilot.backend.investmentplan.application.gateway.planexecution;

import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.SmartInvestmentAmountPolicy;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import java.time.Instant;
import java.util.Optional;

public interface PlanInvestmentFactsGateway {
    Optional<Facts> load(InvestmentPlan plan, PlanPortfolioFundGateway.PortfolioFund fund, Instant businessDate);
    record Facts(SmartInvestmentAmountPolicy.Facts policyFacts, Instant dataDate,
                 String referenceIndexCode, Integer movingAverageDays) {}
}
