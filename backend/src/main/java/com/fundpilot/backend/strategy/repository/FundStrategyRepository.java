package com.fundpilot.backend.strategy.repository;

import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundStrategyRepository extends JpaRepository<FundStrategyEntity, Long> {
}