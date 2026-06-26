package com.fundpilot.backend.strategy.repository;

import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyBacktestRepository extends JpaRepository<StrategyBacktestEntity, Long> {

    /**
     * M-fM-^_M-%M-fM-^_M-^PM-gM--M-^VM-gM-^UM-%M-gM-^IM-^HM-fM-^\M-,M-fM-^\M-/M-eM-^PM-&M-eM--M-^XM-eM-^\M-( {@code passed=true} M-gM-^ZM-^DM-eM-^[M-^^M-fM-5M-^K(issue #10 activate M-fM- M-!M-iM-*M-^L)M-cM-@M-^B
     */
    boolean existsByFundStrategyEntity_IdAndPassedTrue(Long strategyId);

    /**
     * M-fM-^_M-%M-fM-^_M-^PM-gM--M-^VM-gM-^UM-%M-gM-^IM-^HM-fM-^\M-,M-fM-^\M-/M-eM-^@M-hM-?M-^QM-dM-8M-^@M-fM-,M-!M-eM-^[M-^^M-fM-5M-^KM-gM-;M-^SM-fM-^^M-^\(issue #10 getBacktestResult)M-cM-@M-^B
     */
    Optional<StrategyBacktestEntity> findTopByFundStrategyEntity_IdOrderByCreatedDateDesc(Long strategyId);

    /**
     * 查某策略全部回测历史(按创建时间倒序,issue #16 GET /api/strategies/{id}/backtests)。
     */
    List<StrategyBacktestEntity> findByFundStrategyEntity_IdOrderByCreatedDateDesc(Long strategyId);
}