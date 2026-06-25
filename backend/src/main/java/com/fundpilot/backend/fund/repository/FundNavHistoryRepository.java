package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface FundNavHistoryRepository extends JpaRepository<FundNavHistoryEntity, Long> {

    /**
     * 全历史累计净值峰值(issue #9 ADR-0001:不落字段,实时派生)。
     * 用 (fund_id, nav_date) 索引 max 查询。
     */
    @Query("select max(n.accumulatedNav) from FundNavHistoryEntity n where n.fundEntity.id = :fundId")
    Optional<java.math.BigDecimal> findPeakAccumulatedNav(@Param("fundId") Long fundId);

    /**
     * 持仓期内累计净值峰值:加 {@code navDate >= openedAt} 过滤(ADR-0001)。
     */
    @Query("select max(n.accumulatedNav) from FundNavHistoryEntity n where n.fundEntity.id = :fundId and n.navDate >= :since")
    Optional<java.math.BigDecimal> findPeakAccumulatedNavSince(@Param("fundId") Long fundId, @Param("since") Instant since);
}