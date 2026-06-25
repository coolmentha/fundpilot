package com.fundpilot.backend.strategy.repository;

import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StrategyBacktestRepository extends JpaRepository<StrategyBacktestEntity, Long> {

    /**
     * 查某策略版本是否存在 {@code passed=true} 的回测(issue #10 activate 校验)。
     */
    boolean existsByFundStrategyEntity_IdAndPassedTrue(Long strategyId);

    /**
     * 查某策略版本最近一次回测结果(issue #10 getBacktestResult)。
     */
    Optional<StrategyBacktestEntity> findTopByFundStrategyEntity_IdOrderByCreatedDateDesc(Long strategyId);
}