package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.user.entity.UserConfigEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 用户配置视图 DTO(issue #16):只含业务字段,不暴露 Entity 内部字段。
 *
 * @param id                      配置 ID
 * @param totalInvestableCapital  总可投资金
 * @param watchedIndices          关注的大盘指数 secid 列表(已解析,空列表表示用默认)
 * @param createdDate             创建时间
 */
public record UserConfigView(
        Long id,
        BigDecimal totalInvestableCapital,
        List<String> watchedIndices,
        Instant createdDate) {

    public static UserConfigView from(UserConfigEntity config) {
        return new UserConfigView(
                config.getId(),
                config.getTotalInvestableCapital(),
                config.getWatchedIndices() == null || config.getWatchedIndices().isBlank()
                        ? List.of() : List.of(config.getWatchedIndices().split(",")),
                config.getCreatedDate());
    }
}
