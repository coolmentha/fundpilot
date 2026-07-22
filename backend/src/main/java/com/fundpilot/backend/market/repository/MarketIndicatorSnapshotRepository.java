package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketIndicatorSnapshotRepository
        extends JpaRepository<MarketIndicatorSnapshotEntity, Long> {

    /**
     * 按 fund_id + snapshot_date 查询单日快照(软删行由 {@code @SQLRestriction} 自动过滤)。
     * 重跑幂等语义依赖此方法判断「同日是否已存在」。snapshot_date 经 InstantDateConverter 存为 DATE。
     */
    @Query("select s from MarketIndicatorSnapshotEntity s where s.fundCode = (select f.fundCode from FundEntity f where f.id = :fundId) and s.snapshotDate = :snapshotDate")
    Optional<MarketIndicatorSnapshotEntity> findByFundEntity_IdAndSnapshotDate(@Param("fundId") Long fundId, @Param("snapshotDate") Instant snapshotDate);

    /**
     * 按基金代码查共享行情快照；归档用户基金时仅用于解除旧 fund_id 关联。
     */
    @Query("select s from MarketIndicatorSnapshotEntity s where s.fundCode = (select f.fundCode from FundEntity f where f.id = :fundId)")
    List<MarketIndicatorSnapshotEntity> findByFundEntity_Id(@Param("fundId") Long fundId);
}
