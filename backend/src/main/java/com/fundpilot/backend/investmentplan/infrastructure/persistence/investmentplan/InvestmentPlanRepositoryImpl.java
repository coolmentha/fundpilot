package com.fundpilot.backend.investmentplan.infrastructure.persistence.investmentplan;

import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class InvestmentPlanRepositoryImpl implements InvestmentPlanRepository {
    private final InvestmentPlanJpaRepository plans;

    @Override public Optional<InvestmentPlan> findById(long id) { return plans.findById(id).map(this::toDomain); }
    @Override public List<InvestmentPlan> findEffectiveEnabled() {
        return plans.findByStatusAndEnabledTrue(InvestmentPlanStatus.EFFECTIVE.name()).stream().map(this::toDomain).toList();
    }
    @Override public List<InvestmentPlan> findByPortfolioFundId(long portfolioFundId) {
        return plans.findByPortfolioFundId(portfolioFundId).stream().map(this::toDomain).toList();
    }
    @Override public Optional<InvestmentPlan> findEffectiveByPortfolioFundId(long portfolioFundId) {
        return plans.findByPortfolioFundIdAndStatus(portfolioFundId, InvestmentPlanStatus.EFFECTIVE.name())
                .map(this::toDomain);
    }
    @Override public List<InvestmentPlan> findByOwnerId(long ownerId) {
        return plans.findByOwnerIdOrderById(ownerId).stream().map(this::toDomain).toList();
    }
    @Override public InvestmentPlan save(InvestmentPlan plan) {
        InvestmentPlanJpaEntity entity = plan.id() == null ? new InvestmentPlanJpaEntity()
                : plans.findById(plan.id()).orElseThrow();
        entity.setLegacyDcaPlanId(plan.legacyDcaPlanId());
        entity.setPortfolioFundId(plan.portfolioFundId());
        entity.setOwnerId(plan.ownerId());
        entity.setAmount(plan.amount());
        entity.setFrequency(plan.frequency().name());
        entity.setDayOfWeek(plan.dayOfWeek());
        entity.setDayOfMonth(plan.dayOfMonth());
        entity.setAmountStrategy(plan.amountStrategy().name());
        entity.setReferenceIndexCode(plan.referenceIndexCode());
        entity.setMovingAverageDays(plan.movingAverageDays());
        entity.setStatus(plan.status().name());
        entity.setEnabled(plan.enabled());
        return toDomain(plans.save(entity));
    }
    @Override public void delete(InvestmentPlan plan) {
        plans.deleteById(plan.id());
    }
    private InvestmentPlan toDomain(InvestmentPlanJpaEntity entity) {
        return InvestmentPlan.rehydrate(entity.getId(), entity.getLegacyDcaPlanId(), entity.getPortfolioFundId(),
                entity.getOwnerId(), entity.isEnabled(), entity.getAmount(),
                InvestmentPlanFrequency.valueOf(entity.getFrequency()), entity.getDayOfWeek(), entity.getDayOfMonth(),
                InvestmentPlanAmountStrategy.valueOf(entity.getAmountStrategy() == null ? "FIXED"
                        : entity.getAmountStrategy()), entity.getReferenceIndexCode(), entity.getMovingAverageDays(),
                InvestmentPlanStatus.valueOf(entity.getStatus()), entity.getCreatedDate());
    }
}
