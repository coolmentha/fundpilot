package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundRepository extends JpaRepository<FundEntity, Long> {
}