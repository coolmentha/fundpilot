package com.fundpilot.backend.discipline.infrastructure.persistence.strategy;
import com.fundpilot.backend.discipline.domain.strategy.*; import java.util.List; import java.util.Optional;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor class DisciplineStrategyRepositoryImpl implements DisciplineStrategyRepository {
    private final DisciplineStrategyJpaRepository strategies;
    @Override public Optional<DisciplineStrategy> findById(long id) { return strategies.findById(id).map(this::toDomain); }
    @Override public Optional<DisciplineStrategy> findEffectiveByPortfolioFundId(long id) { return strategies.findByPortfolioFundIdAndStatus(id, "EFFECTIVE").map(this::toDomain); }
    @Override public List<DisciplineStrategy> findEffective() { return strategies.findByStatus("EFFECTIVE").stream().map(this::toDomain).toList(); }
    @Override public List<DisciplineStrategy> findByPortfolioFundId(long id) { return strategies.findByPortfolioFundId(id).stream().map(this::toDomain).toList(); }
    @Override public Optional<DisciplineStrategy> findByTriggeredAdviceId(long adviceId) { return strategies.findByTriggeredAdviceId(adviceId).map(this::toDomain); }
    @Override public DisciplineStrategy save(DisciplineStrategy value) {
        DisciplineStrategyJpaEntity entity = value.id() == null ? new DisciplineStrategyJpaEntity() : strategies.findById(value.id()).orElseThrow();
        entity.setPortfolioFundId(value.portfolioFundId()); entity.setOwnerId(value.ownerId()); entity.setStatus(value.status());
        entity.setActivation(value.activation()); entity.setPullback(value.pullback()); entity.setHarvest(value.harvest());
        entity.setMinimumHolding(value.minimumHolding()); entity.setMaxSingleSell(value.maxSingleSell()); entity.setCooldownDays(value.cooldownDays());
        entity.setPresetCategory(value.presetCategory()); entity.setPresetVersion(value.presetVersion());
        entity.setCustomized(value.customized());
        entity.setTakeProfitPhase(value.takeProfitPhase()); entity.setCycleStartedAt(value.cycleStartedAt());
        entity.setCyclePeakNav(value.cyclePeakNav()); entity.setTriggeredAdviceId(value.triggeredAdviceId());
        entity.setCooldownStartedAt(value.cooldownStartedAt());
        return toDomain(strategies.save(entity));
    }
    private DisciplineStrategy toDomain(DisciplineStrategyJpaEntity value) { return DisciplineStrategy.rehydrate(value.getId(), value.getPortfolioFundId(), value.getOwnerId(), value.getStatus(), value.getActivation(), value.getPullback(), value.getHarvest(), value.getMinimumHolding(), value.getMaxSingleSell(), value.getCooldownDays(), value.getPresetCategory(), value.getPresetVersion(), value.isCustomized(), value.getTakeProfitPhase(), value.getCycleStartedAt(), value.getCyclePeakNav(), value.getTriggeredAdviceId(), value.getCooldownStartedAt()); }
}
