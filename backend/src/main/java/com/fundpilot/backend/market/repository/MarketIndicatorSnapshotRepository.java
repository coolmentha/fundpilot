package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MarketIndicatorSnapshotRepository
        extends JpaRepository<MarketIndicatorSnapshotEntity, Long> {

    /**
     * 按 fund_id + snapshot_date 查询单日快照(软删行由 {@code @SQLRestriction} 自动过滤)。
     * 重跑幂等语义依赖此方法判断「同日是否已存在」。
     */
    Optional<MarketIndicatorSnapshotEntity> findByFundEntity_IdAndSnapshotDate(Long fundId, LocalDate snapshotDate);
}
