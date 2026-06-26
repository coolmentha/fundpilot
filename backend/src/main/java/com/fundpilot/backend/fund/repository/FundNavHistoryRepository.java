package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /**
     * 按日期范围升序查净值序列(issue #11 回测引擎用)。
     */
    List<FundNavHistoryEntity> findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(
            Long fundId, Instant start, Instant end);

    /**
     * 查某基金最早净值日期(issue #11 窗口降级用)。
     */
    @Query("select min(n.navDate) from FundNavHistoryEntity n where n.fundEntity.id = :fundId")
    Optional<Instant> findEarliestNavDate(@Param("fundId") Long fundId);

    /**
     * 查某基金最近 N 条净值(按日期降序取前 N,issue #13 单周跌幅用)。
     * 供 {@code WeeklyDropCalculator} 算 [T-5, T-1] 两点跌幅。
     */
    List<FundNavHistoryEntity> findTop5ByFundEntity_IdOrderByNavDateDesc(Long fundId);

    /**
     * 查某基金某日(UTC 0点起 24 小时区间)的净值行(issue #15 NavConfirmJob 回填 PENDING 交易用)。
     * 取区间内第一行(净值公布当日只应有一行)。
     */
    List<FundNavHistoryEntity> findByFundEntity_IdAndNavDateBetween(Long fundId, Instant start, Instant end);
}