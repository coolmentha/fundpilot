package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.user.entity.UserConfigEntity;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

/**
 * 用户配置视图 DTO:返回总资金池与关注指数列表。
 *
 * @param id              配置 ID
 * @param totalCapital    总资金池;null 表示尚未发生外部入金
 * @param watchedIndices  关注的大盘指数 secid 列表(空列表表示未配置,用默认)
 * @param createdDate     创建时间
 */
public record UserConfigView(
        Long id,
        BigDecimal totalCapital,
        List<String> watchedIndices,
        Instant createdDate) {

    public static UserConfigView from(UserConfigEntity config) {
        if (config == null) {
            return new UserConfigView(null, null, List.of(), null);
        }
        return new UserConfigView(
                config.getId(),
                config.getTotalCapital(),
                config.getWatchedIndices() == null || config.getWatchedIndices().isBlank()
                        ? List.of() : List.of(config.getWatchedIndices().split(",")),
                config.getCreatedDate());
    }
}
