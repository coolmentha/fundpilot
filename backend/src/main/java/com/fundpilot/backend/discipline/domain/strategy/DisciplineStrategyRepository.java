package com.fundpilot.backend.discipline.domain.strategy;
import java.util.List; import java.util.Optional;
public interface DisciplineStrategyRepository {
    Optional<DisciplineStrategy> findById(long id);
    Optional<DisciplineStrategy> findEffectiveByPortfolioFundId(long portfolioFundId);
    List<DisciplineStrategy> findEffective();
    List<DisciplineStrategy> findByPortfolioFundId(long portfolioFundId);
    DisciplineStrategy save(DisciplineStrategy strategy);
}
