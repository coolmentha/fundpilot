package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.NavConfirmService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 管理端/操作确认 Controller(issue #15):手动触发净值确认任务(调试/补跑用)。
 */
@RestController
public class AdminTransactionController {

    private final NavConfirmService navConfirmService;

    public AdminTransactionController(NavConfirmService navConfirmService) {
        this.navConfirmService = navConfirmService;
    }

    @PostMapping("/api/admin/transactions/confirm-nav")
    public ApiResponse<Map<String, Object>> confirmNav() {
        int confirmed = navConfirmService.confirmPendingTransactions(LocalDate.now());
        return ApiResponse.ok(Map.of("confirmed", confirmed));
    }
}
