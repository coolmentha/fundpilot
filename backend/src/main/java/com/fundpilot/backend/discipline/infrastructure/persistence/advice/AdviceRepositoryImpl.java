package com.fundpilot.backend.discipline.infrastructure.persistence.advice;

import com.fundpilot.backend.discipline.domain.advice.Advice;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.advice.AdviceResponseStatus;
import java.util.Optional;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AdviceRepositoryImpl implements AdviceRepository {
    private final DisciplineAdviceJpaRepository advice;

    @Override
    public Optional<Advice> findByIdForUpdate(long adviceId) {
        return advice.findByIdForUpdate(adviceId).map(AdviceRepositoryImpl::toDomain);
    }

    @Override public java.util.List<Advice> findPendingByOwner(long ownerId) {
        return advice.findByOwnerIdAndResponseStatusOrderBySignalDateDesc(ownerId, "PENDING").stream()
                .map(AdviceRepositoryImpl::toDomain).toList();
    }
    @Override public java.util.List<Advice> findByPortfolioFundAndSignalDateBetween(long portfolioFundId,
                                                                                       Instant fromInclusive, Instant toExclusive) {
        return advice.findByPortfolioFundIdAndSignalDateGreaterThanEqualAndSignalDateLessThanOrderBySignalDateDesc(
                portfolioFundId, fromInclusive, toExclusive).stream().map(AdviceRepositoryImpl::toDomain).toList();
    }
    @Override public Optional<Advice> findLatestByPortfolioFund(long portfolioFundId) {
        return advice.findFirstByPortfolioFundIdOrderBySignalDateDesc(portfolioFundId).map(AdviceRepositoryImpl::toDomain);
    }

    @Override
    public Advice save(Advice value) {
        DisciplineAdviceJpaEntity entity = advice.findById(value.id())
                .orElseThrow(() -> new IllegalStateException("建议不存在: " + value.id()));
        entity.setIgnoredDate(value.ignoredAt());
        entity.setResponseStatus(value.responseStatus().name());
        return toDomain(advice.save(entity));
    }

    @Override
    public Advice replaceGenerated(long portfolioFundId, long ownerId, long disciplineStrategyId,
                                 Instant signalDate,
                                 AdviceAction action, Integer triggerTier, java.math.BigDecimal coefficient,
                                 java.math.BigDecimal suggestedValue, String suggestedMeasureUnit, String reason,
                                 String warnings, String hardConstraintBreaches) {
        DisciplineAdviceJpaEntity entity = advice.findByPortfolioFundIdAndSignalDate(portfolioFundId, signalDate)
                .orElseGet(DisciplineAdviceJpaEntity::new);
        if (!"PENDING".equals(entity.getResponseStatus()) && entity.getId() != null) {
            return toDomain(entity);
        }
        entity.setPortfolioFundId(portfolioFundId);
        entity.setOwnerId(ownerId);
        entity.setDisciplineStrategyId(disciplineStrategyId);
        entity.setSignalDate(signalDate);
        entity.setSignalType((short) action.ordinal());
        entity.setTriggerTier(triggerTier); entity.setCoefficient(coefficient); entity.setSuggestedValue(suggestedValue);
        entity.setSuggestedMeasureUnit(suggestedMeasureUnit); entity.setReason(reason); entity.setWarnings(warnings);
        entity.setHardConstraintBreaches(hardConstraintBreaches);
        entity.setIgnoredDate(null);
        entity.setResponseStatus(AdviceResponseStatus.PENDING.name());
        return toDomain(advice.save(entity));
    }

    private static Advice toDomain(DisciplineAdviceJpaEntity entity) {
        return Advice.rehydrate(entity.getId(), entity.getPortfolioFundId(), entity.getOwnerId(),
                actionOf(entity.getSignalType()), entity.getSignalDate(), entity.getTriggerTier(), entity.getCoefficient(),
                entity.getSuggestedValue(), entity.getSuggestedMeasureUnit(), entity.getReason(), entity.getWarnings(), entity.getIgnoredDate(),
                AdviceResponseStatus.valueOf(entity.getResponseStatus()));
    }

    private static AdviceAction actionOf(Short value) {
        return switch (value == null ? 0 : value) {
            case 0 -> AdviceAction.NONE;
            case 1 -> AdviceAction.BUILD;
            case 2 -> AdviceAction.ADD;
            case 3 -> AdviceAction.SELL;
            default -> throw new IllegalStateException("未知建议类型编码: " + value);
        };
    }
}
