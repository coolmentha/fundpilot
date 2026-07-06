package com.fundpilot.backend.dca.controller;

import com.fundpilot.backend.dca.enums.DcaFrequency;

import java.math.BigDecimal;

/**
 * 定投计划配置请求。
 *
 * @param enabled    是否启用(EFFECTIVE 状态下若 false 则 Job 跳过)
 * @param amount     每次定投金额(元)
 * @param frequency  定投频率(WEEKLY/MONTHLY)
 * @param dayOfWeek  周定投日(1=周一...7=周日),WEEKLY 时必填
 * @param dayOfMonth 月定投日(1-28),MONTHLY 时必填
 */
public record DcaPlanRequest(
        Boolean enabled,
        BigDecimal amount,
        DcaFrequency frequency,
        Integer dayOfWeek,
        Integer dayOfMonth) {
}
