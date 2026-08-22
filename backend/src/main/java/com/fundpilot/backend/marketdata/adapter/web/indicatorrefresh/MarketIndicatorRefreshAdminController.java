package com.fundpilot.backend.marketdata.adapter.web.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理 - 行情指标刷新接口", description = "行情指标刷新相关操作")
@RestController
@RequestMapping("/api/admin/market-data")
@RequiredArgsConstructor
public class MarketIndicatorRefreshAdminController {
    private final MarketIndicatorRefreshCommandHandler commands;

    @PostMapping("/refresh")
    @Operation(summary = "手动刷新行情指标")
    public ApiResponse<Map<String, String>> refresh() {
        commands.refreshAll();
        return ApiResponse.ok(Map.of("status", "refreshed"));
    }
}
