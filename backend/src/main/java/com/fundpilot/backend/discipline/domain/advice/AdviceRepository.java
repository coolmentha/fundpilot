package com.fundpilot.backend.discipline.domain.advice;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

/** Advice 聚合的持久化需求。 */
public interface AdviceRepository {
    Optional<Advice> findByIdForUpdate(long adviceId);
    List<Advice> findPendingByOwner(long ownerId);
    List<Advice> findByPortfolioFundAndSignalDateBetween(long portfolioFundId, Instant fromInclusive,
                                                          Instant toExclusive);
    Optional<Advice> findLatestByPortfolioFund(long portfolioFundId);

    Advice save(Advice advice);

    /** 同组合基金、同业务日的待回应建议覆盖式写入，供行情重跑更新建议内容。 */
    Advice replaceGenerated(long portfolioFundId, long ownerId, long disciplineStrategyId,
                          Instant signalDate,
                          AdviceAction action, Integer triggerTier, java.math.BigDecimal coefficient,
                          java.math.BigDecimal suggestedValue, String suggestedMeasureUnit, String reason,
                          String warnings, String hardConstraintBreaches);
}
