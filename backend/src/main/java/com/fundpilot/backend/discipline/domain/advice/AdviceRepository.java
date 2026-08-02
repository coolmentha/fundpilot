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

    /** 同组合基金最新一条未决卖出建议(PENDING 或 RESPONDED)，供生成侧做在途卖出抑制。 */
    Optional<Advice> findLatestSellAdviceByPortfolioFund(long portfolioFundId);

    /** 同组合基金指定业务日的建议，供"今日建议"查询。 */
    Optional<Advice> findByPortfolioFundAndSignalDate(long portfolioFundId, Instant signalDate);

    Advice save(Advice advice);

    /** 同组合基金、同业务日的待回应建议覆盖式写入，供行情重跑更新建议内容。 */
    Advice replaceGenerated(long portfolioFundId, long ownerId, long disciplineStrategyId,
                          Instant signalDate,
                          AdviceAction action, Integer triggerTier, java.math.BigDecimal coefficient,
                          java.math.BigDecimal suggestedValue, String suggestedMeasureUnit, String reason,
                          String warnings, String hardConstraintBreaches);
}
