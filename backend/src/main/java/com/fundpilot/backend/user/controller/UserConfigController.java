package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.user.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户配置 Controller:单用户场景,管理关注指数列表。
 * <p>GET 取配置(未初始化返默认);PUT 更新关注指数。逻辑下沉 {@link UserConfigService}。
 */
@RestController
@RequestMapping("/api/user-config")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigService userConfigService;

    @GetMapping
    public ApiResponse<UserConfigView> get() {
        return ApiResponse.ok(userConfigService.get());
    }

    @PutMapping
    public ApiResponse<UserConfigView> update(@RequestBody UserConfigUpdateRequest request) {
        return ApiResponse.ok(userConfigService.update(request.watchedIndices()));
    }

    public record UserConfigUpdateRequest(List<String> watchedIndices) {
    }
}
