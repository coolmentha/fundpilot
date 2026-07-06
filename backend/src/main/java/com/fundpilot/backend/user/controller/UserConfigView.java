package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.user.entity.UserConfigEntity;

import java.time.Instant;
import java.util.List;

/**
 * 用户配置视图 DTO:行情工作台转向后,只剩关注指数列表。
 *
 * @param id              配置 ID
 * @param watchedIndices  关注的大盘指数 secid 列表(空列表表示未配置,用默认)
 * @param createdDate     创建时间
 */
public record UserConfigView(
        Long id,
        List<String> watchedIndices,
        Instant createdDate) {

    public static UserConfigView from(UserConfigEntity config) {
        if (config == null) {
            return new UserConfigView(null, List.of(), null);
        }
        return new UserConfigView(
                config.getId(),
                config.getWatchedIndices() == null || config.getWatchedIndices().isBlank()
                        ? List.of() : List.of(config.getWatchedIndices().split(",")),
                config.getCreatedDate());
    }
}
