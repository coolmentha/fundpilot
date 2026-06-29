package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.NavConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 管理端/操作确认 Controller(issue #15):手动触发净值确认任务(调试/补跑用)。
 */
@RestController
@RequiredArgsConstructor
public class AdminTransactionController {

    private final NavConfirmService navConfirmService;

    @PostMapping("/api/admin/transactions/confirm-nav")
    public ApiResponse<Map<String, Object>> confirmNav() {
        int confirmed = navConfirmService.confirmPendingTransactions(Instant.now());
        return ApiResponse.ok(Map.of("confirmed", confirmed));
    }
}
