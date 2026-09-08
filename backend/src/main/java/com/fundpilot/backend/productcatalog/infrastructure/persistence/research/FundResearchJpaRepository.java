package com.fundpilot.backend.productcatalog.infrastructure.persistence.research;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FundResearchJpaRepository extends JpaRepository<FundResearchJpaEntity, Long> {
    Optional<FundResearchJpaEntity> findByFundCode(String fundCode);

    @Query(value = """
            SELECT p.fund_code
            FROM portfolio_fund pf
            JOIN fund_product p ON p.id = pf.fund_product_id AND p.deleted_date IS NULL
            LEFT JOIN fund_research r ON r.fund_code = p.fund_code
            WHERE pf.validity = 'TRACKED'
            GROUP BY p.fund_code
            ORDER BY min(coalesce(r.last_attempt_at, TIMESTAMPTZ 'epoch')), p.fund_code
            """, nativeQuery = true)
    List<String> findTrackedFundCodes(Pageable pageable);
}
