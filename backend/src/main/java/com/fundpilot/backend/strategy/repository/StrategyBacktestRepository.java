package com.fundpilot.backend.strategy.repository;

import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyBacktestRepository extends JpaRepository<StrategyBacktestEntity, Long> {
}