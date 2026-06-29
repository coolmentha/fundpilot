package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.signal.service.SignalGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 管理/调试入口(issue #13):手动触发当日全量信号生成。
 * 供运维或开发在定时任务之外手动重跑(如某只基金数据补齐后重算)。
 */
@RestController
@RequestMapping("/api/admin/signals")
@RequiredArgsConstructor
public class AdminSignalController {

    private final SignalGenerationService signalGenerationService;

    @PostMapping("/generate")
    public ApiResponse<Map<String, String>> generate() {
        signalGenerationService.generateDailySignals(Instant.now());
        return ApiResponse.ok(Map.of("status", "generated"));
    }
}
