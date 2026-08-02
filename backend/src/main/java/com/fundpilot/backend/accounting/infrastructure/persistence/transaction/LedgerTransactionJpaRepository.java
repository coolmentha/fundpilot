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

    List<LedgerTransactionJpaEntity> findByPortfolioFundIdInAndStatus(Collection<Long> portfolioFundIds,
                                                                        String status);

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

    @Query("select (count(t) > 0) from LedgerTransactionJpaEntity t "
            + "where t.investmentPlanId = :investmentPlanId and t.tradeDate >= :start and t.tradeDate < :end")
    boolean existsByInvestmentPlanIdAndTradeDateBetween(@Param("investmentPlanId") Long investmentPlanId,
                                                         @Param("start") Instant start, @Param("end") Instant end);

    interface InvestmentPlanOccurrenceProjection {
        Long getInvestmentPlanId();
        Instant getTradeDate();
        java.math.BigDecimal getAmount();
        String getStatus();
    }

    @Query(value = "select t.investment_plan_id as investmentPlanId, t.trade_date as tradeDate, "
            + "t.amount as amount, t.status as status from fund_transaction t "
            + "join investment_plan p on p.id = t.investment_plan_id "
            + "where p.owner_id = :ownerId and t.source = 'INVEST' and t.trade_date >= :start "
            + "and t.trade_date < :end and t.deleted_date is null", nativeQuery = true)
    List<InvestmentPlanOccurrenceProjection> findInvestmentPlanOccurrences(@Param("ownerId") Long ownerId,
                                                                             @Param("start") Instant start,
                                                                             @Param("end") Instant end);

    @Query(value = "select coalesce(sum(t.amount), 0) from fund_transaction t "
            + "join portfolio_fund p on p.id = t.portfolio_fund_id "
            + "where p.owner_id = :ownerId and p.validity = 'TRACKED' and t.source = 'INVEST' "
            + "and t.status <> 'CANCELLED' and t.trade_date >= :start and t.trade_date < :end "
            + "and t.deleted_date is null", nativeQuery = true)
    java.math.BigDecimal sumInvestedAmount(@Param("ownerId") Long ownerId, @Param("start") Instant start,
                                           @Param("end") Instant end);

    boolean existsByDisciplineAdviceIdAndStatusNot(Long disciplineAdviceId, String status);

    @Query("select t from LedgerTransactionJpaEntity t where t.disciplineAdviceId = :disciplineAdviceId "
            + "order by t.createdDate desc")
    List<LedgerTransactionJpaEntity> findByDisciplineAdviceId(@Param("disciplineAdviceId") Long disciplineAdviceId);
}
