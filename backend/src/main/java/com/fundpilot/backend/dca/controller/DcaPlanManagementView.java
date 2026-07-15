package com.fundpilot.backend.dca.controller;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 全局定投管理页使用的计划、基金与本月剩余执行投影。 */
public record DcaPlanManagementView(
        Long id,
        Long fundId,
        String fundCode,
        String fundName,
        Boolean enabled,
        BigDecimal amount,
        DcaFrequency frequency,
        Integer dayOfWeek,
        Integer dayOfMonth,
        DcaPlanStatus status,
        Instant createdDate,
        int remainingOccurrences,
        BigDecimal remainingAmount,
        List<Instant> remainingExecutionDates) {

    public static DcaPlanManagementView from(FundDcaPlanEntity plan, List<Instant> executionDates) {
        List<Instant> dates = List.copyOf(executionDates);
        BigDecimal remaining = plan.getAmount().multiply(BigDecimal.valueOf(dates.size()));
        return new DcaPlanManagementView(
                plan.getId(),
                plan.getFundEntity().getId(),
                plan.getFundEntity().getFundCode(),
                plan.getFundEntity().getFundName(),
                plan.getEnabled(),
                plan.getAmount(),
                plan.getFrequency(),
                plan.getDayOfWeek(),
                plan.getDayOfMonth(),
                plan.getStatus(),
                plan.getCreatedDate(),
                dates.size(),
                remaining,
                dates);
    }
}
