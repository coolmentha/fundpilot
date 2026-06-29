package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.user.entity.UserConfigEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 用户配置视图 DTO(issue #16):只含业务字段,不暴露 Entity 内部字段。
 *
 * @param id                      配置 ID
 * @param totalInvestableCapital  总可投资金
 * @param createdDate             创建时间
 */
public record UserConfigView(
        Long id,
        BigDecimal totalInvestableCapital,
        Instant createdDate) {

    public static UserConfigView from(UserConfigEntity config) {
        return new UserConfigView(config.getId(), config.getTotalInvestableCapital(), config.getCreatedDate());
    }
}
