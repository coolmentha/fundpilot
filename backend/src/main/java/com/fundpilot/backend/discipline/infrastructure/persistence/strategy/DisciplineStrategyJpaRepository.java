package com.fundpilot.backend.discipline.infrastructure.persistence.strategy;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
interface DisciplineStrategyJpaRepository extends JpaRepository<DisciplineStrategyJpaEntity, Long> {
    Optional<DisciplineStrategyJpaEntity> findByPortfolioFundIdAndStatus(Long portfolioFundId, String status);
    List<DisciplineStrategyJpaEntity> findByPortfolioFundId(Long portfolioFundId);
    List<DisciplineStrategyJpaEntity> findByStatus(String status);
    Optional<DisciplineStrategyJpaEntity> findByTriggeredAdviceId(Long triggeredAdviceId);
}
