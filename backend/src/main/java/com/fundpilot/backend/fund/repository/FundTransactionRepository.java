package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundTransactionRepository extends JpaRepository<FundTransactionEntity, Long> {
}