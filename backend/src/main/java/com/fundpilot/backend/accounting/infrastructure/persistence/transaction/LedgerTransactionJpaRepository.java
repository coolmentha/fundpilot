package com.fundpilot.backend.accounting.infrastructure.persistence.transaction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface LedgerTransactionJpaRepository extends JpaRepository<LedgerTransactionJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LedgerTransactionJpaEntity t where t.id = :id")
    Optional<LedgerTransactionJpaEntity> findByIdForUpdate(@Param("id") Long id);

    List<LedgerTransactionJpaEntity> findByPortfolioFundIdAndStatus(Long portfolioFundId, String status);

    boolean existsByPortfolioFundIdAndStatus(Long portfolioFundId, String status);

    List<LedgerTransactionJpaEntity> findByStatus(String status);

    @Query("select t from LedgerTransactionJpaEntity t where t.status = :status "
            + "order by coalesce(t.tradeDate, t.createdDate) desc, t.createdDate desc")
    List<LedgerTransactionJpaEntity> findByStatusOrderByTradeDateDesc(@Param("status") String status);

    @Query("select t from LedgerTransactionJpaEntity t where t.portfolioFundId = :portfolioFundId "
            + "order by coalesce(t.tradeDate, t.createdDate) desc, t.createdDate desc")
    List<LedgerTransactionJpaEntity> findByPortfolioFundOrderByTradeDateDesc(
            @Param("portfolioFundId") Long portfolioFundId);

    interface HoldingSharesProjection {
        Long getPortfolioFundId();

        java.math.BigDecimal getHoldingShares();
    }

    @Query(value = "select portfolio_fund_id as portfolioFundId, "
            + "coalesce(sum(case when source in ('INCREASE','TRANSFER_IN','INVEST','ADJUST_IN') "
            + "then shares else -shares end), 0) as holdingShares "
            + "from fund_transaction where status = 'CONFIRMED' and deleted_date is null "
            + "and portfolio_fund_id in (:portfolioFundIds) group by portfolio_fund_id",
            nativeQuery = true)
    List<HoldingSharesProjection> aggregateConfirmedShares(
            @Param("portfolioFundIds") Collection<Long> portfolioFundIds);

    boolean existsByDcaPlanIdAndTradeDateBetween(Long dcaPlanId, Instant start, Instant end);
}
