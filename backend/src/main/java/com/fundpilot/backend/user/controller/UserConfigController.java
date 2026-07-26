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
 * 用户配置 Controller:管理可选月度定投预算。
 * <p>GET 取配置;PUT 覆盖配置。逻辑下沉 {@link UserConfigService}。
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
        return ApiResponse.ok(userConfigService.update(request.monthlyDcaBudget()));
    }

    public record UserConfigUpdateRequest(BigDecimal monthlyDcaBudget) {
    }
}
