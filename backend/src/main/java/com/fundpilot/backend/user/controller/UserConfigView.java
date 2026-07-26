package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.user.entity.UserConfigEntity;

import java.time.Instant;
import java.math.BigDecimal;

/**
 * 用户配置视图 DTO:返回月度定投预算。
 *
 * @param id              配置 ID
 * @param monthlyDcaBudget 可选月度定投预算;null 表示不比较预算
 * @param createdDate     创建时间
 */
public record UserConfigView(
        Long id,
        BigDecimal monthlyDcaBudget,
        Instant createdDate) {

    public static UserConfigView from(UserConfigEntity config) {
        if (config == null) {
            return new UserConfigView(null, null, null);
        }
        return new UserConfigView(
                config.getId(),
                config.getMonthlyDcaBudget(),
                config.getCreatedDate());
    }
}
