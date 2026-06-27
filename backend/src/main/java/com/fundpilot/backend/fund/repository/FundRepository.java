package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundRepository extends JpaRepository<FundEntity, Long> {

    /**
     * 查所有 fundSubType 为 null 的行,供 {@code FundDictBackfillService.backfillAll} 批量回填。
     */
    List<FundEntity> findByFundSubTypeIsNull();

    /**
     * 查指定状态的基金(issue #18 概览页盈亏聚合用,取所有 HOLDING 基金)。
     */
    List<FundEntity> findByStatus(FundStatus status);
}