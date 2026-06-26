package com.fundpilot.backend.user.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户配置 Controller(issue #16):单用户场景,只有一行 UserConfig。
 * <p>GET 取唯一配置;PUT 更新 totalInvestableCapital(无则新建)。
 */
@RestController
@RequestMapping("/api/user-config")
public class UserConfigController {

    private final UserConfigRepository userConfigRepository;

    public UserConfigController(UserConfigRepository userConfigRepository) {
        this.userConfigRepository = userConfigRepository;
    }

    @GetMapping
    public ApiResponse<UserConfigEntity> get() {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        return ApiResponse.ok(all.isEmpty() ? null : all.get(0));
    }

    @PutMapping
    public ApiResponse<UserConfigEntity> update(@RequestBody UserConfigUpdateRequest request) {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        UserConfigEntity config = all.isEmpty() ? new UserConfigEntity() : all.get(0);
        if (request.totalInvestableCapital() != null) {
            config.setTotalInvestableCapital(request.totalInvestableCapital());
        }
        return ApiResponse.ok(userConfigRepository.save(config));
    }

    public record UserConfigUpdateRequest(BigDecimal totalInvestableCapital) {
    }
}
