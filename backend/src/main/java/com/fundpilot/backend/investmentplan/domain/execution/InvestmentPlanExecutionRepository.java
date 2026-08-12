package com.fundpilot.backend.investmentplan.domain.execution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvestmentPlanExecutionRepository {
    Optional<InvestmentPlanExecution> find(long planId, Instant businessDate);
    boolean existsBetween(long planId, Instant startInclusive, Instant endExclusive);
    List<InvestmentPlanExecution> findLatestByPlanIds(List<Long> planIds);
    List<InvestmentPlanExecution> findBetween(List<Long> planIds, Instant startInclusive, Instant endExclusive);
    boolean insert(InvestmentPlanExecution execution);
}
