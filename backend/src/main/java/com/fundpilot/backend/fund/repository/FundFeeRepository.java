package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundFeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundFeeRepository extends JpaRepository<FundFeeEntity, Long> {

    /** 按 fund_code 查费率(唯一索引,至多一行)。 */
    Optional<FundFeeEntity> findByFundCode(String fundCode);

    boolean existsByFundCode(String fundCode);
}
