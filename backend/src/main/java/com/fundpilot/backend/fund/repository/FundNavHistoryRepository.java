package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundNavHistoryRepository extends JpaRepository<FundNavHistoryEntity, Long> {
}