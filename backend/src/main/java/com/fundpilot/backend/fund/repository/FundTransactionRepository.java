package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundTransactionRepository extends JpaRepository<FundTransactionEntity, Long> {

    /**
     * 按 fund_id + status 查交易行,供 {@code FundPositionService} 聚合持仓/在途份额。
     * 软删行由 {@code @SQLRestriction} 自动过滤。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndStatus(Long fundId, FundTransactionStatus status);
}