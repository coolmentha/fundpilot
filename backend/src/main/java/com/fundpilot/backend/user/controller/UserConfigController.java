package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.user.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 用户配置 Controller(issue #16):单用户场景,只有一行 UserConfig。
 * <p>GET 取唯一配置;PUT 更新 totalInvestableCapital(无则新建)。
 * 逻辑下沉 {@link UserConfigService},返回 {@link UserConfigView} DTO。
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
        return ApiResponse.ok(userConfigService.update(request.totalInvestableCapital()));
    }

    public record UserConfigUpdateRequest(BigDecimal totalInvestableCapital) {
    }
}
