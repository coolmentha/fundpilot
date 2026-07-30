package com.fundpilot.backend.productcatalog.infrastructure.persistence.fee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FundFeeScheduleJpaRepository extends JpaRepository<FundFeeScheduleJpaEntity, Long> {
    Optional<FundFeeScheduleJpaEntity> findByFundCode(String fundCode);

    @Query("select f.fundCode from FundFeeScheduleJpaEntity f order by f.fundCode")
    List<String> findAllFundCodes();
}
