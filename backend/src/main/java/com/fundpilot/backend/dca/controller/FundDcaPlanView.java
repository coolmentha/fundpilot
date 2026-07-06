package com.fundpilot.backend.dca.controller;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 定投计划视图 DTO。
 */
public record FundDcaPlanView(
        Long id,
        Long fundId,
        Boolean enabled,
        BigDecimal amount,
        DcaFrequency frequency,
        Integer dayOfWeek,
        Integer dayOfMonth,
        DcaPlanStatus status,
        Instant createdDate) {

    public static FundDcaPlanView from(FundDcaPlanEntity plan) {
        return new FundDcaPlanView(
                plan.getId(),
                plan.getFundEntity() != null ? plan.getFundEntity().getId() : null,
                plan.getEnabled(),
                plan.getAmount(),
                plan.getFrequency(),
                plan.getDayOfWeek(),
                plan.getDayOfMonth(),
                plan.getStatus(),
                plan.getCreatedDate());
    }
}
