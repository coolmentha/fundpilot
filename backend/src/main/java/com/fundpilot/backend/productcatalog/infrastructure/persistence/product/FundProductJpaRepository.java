package com.fundpilot.backend.productcatalog.infrastructure.persistence.product;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FundProductJpaRepository extends JpaRepository<FundProductJpaEntity, Long> {
    Optional<FundProductJpaEntity> findByFundCode(String fundCode);
    List<FundProductJpaEntity> findByFundCodeIn(Set<String> fundCodes);

    @Query("SELECT p FROM FundProductJpaEntity p " +
            "WHERE LOWER(p.fundCode) LIKE LOWER(CONCAT(:query, '%')) " +
            "OR LOWER(p.fundName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY p.fundCode")
    List<FundProductJpaEntity> search(@Param("query") String query, Pageable pageable);
}
