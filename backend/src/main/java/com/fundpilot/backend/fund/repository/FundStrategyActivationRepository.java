package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundStrategyActivationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FundStrategyActivationRepository extends JpaRepository<FundStrategyActivationEntity, Long> {

    /**
     * 查某策略版本当前未停用的任期(issue #10 activate/retire 回填 deactivatedAt)。
     */
    Optional<FundStrategyActivationEntity> findByFundStrategyEntity_IdAndDeactivatedAtIsNull(Long strategyId);

    /**
     * 查某基金所有未停用的任期(issue #10 CLEARED 全员回退时批量回填)。
     */
    List<FundStrategyActivationEntity> findByFundEntity_IdAndDeactivatedAtIsNull(Long fundId);
}
