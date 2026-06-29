package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.FundDictSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理/调试入口(ADR-0005):手动触发基金字典同步(拉东方财富全量字典 upsert 到 fund_dict 表)。
 * 供运维或开发在定时任务之外手动重跑(如首次部署后预填字典、或某次同步失败后补数据)。
 */
@RestController
@RequestMapping("/api/admin/fund-dict")
@RequiredArgsConstructor
public class AdminFundDictController {

    private final FundDictSyncService fundDictSyncService;

    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync() {
        int upserted = fundDictSyncService.syncAll();
        return ApiResponse.ok(Map.of("status", "synced", "upserted", upserted));
    }
}
