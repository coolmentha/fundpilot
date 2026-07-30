package com.fundpilot.backend.accounting.infrastructure.persistence.lot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface LotJpaRepository extends JpaRepository<LotJpaEntity, Long> {

    /** FIFO 匹配：剩余份额大于 0 的 lot，按买入交易时间升序，同刻按 ID 升序保证稳定。 */
    @Query("select l from LotJpaEntity l where l.portfolioFundId = :portfolioFundId "
            + "and l.remainingShares > 0 order by l.acquireDate asc, l.id asc")
    List<LotJpaEntity> findOpenLotsOrderByAcquireDate(@Param("portfolioFundId") Long portfolioFundId);

    List<LotJpaEntity> findByPortfolioFundIdOrderByAcquireDateAsc(Long portfolioFundId);

    List<LotJpaEntity> findByPortfolioFundIdIn(Collection<Long> portfolioFundIds);
}
