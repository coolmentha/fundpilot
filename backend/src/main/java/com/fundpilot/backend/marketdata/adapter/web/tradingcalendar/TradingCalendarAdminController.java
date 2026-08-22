package com.fundpilot.backend.marketdata.adapter.web.tradingcalendar;

import com.fundpilot.backend.marketdata.application.command.tradingcalendar.TradingCalendarCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理 - 交易日历接口", description = "交易日历相关操作")
@RestController
@RequestMapping("/api/admin/market-data")
@RequiredArgsConstructor
public class TradingCalendarAdminController {
    private final TradingCalendarCommandHandler commands;

    @PostMapping("/sync-trading-calendar")
    @Operation(summary = "补写交易日历")
    public ApiResponse<Map<String, Object>> synchronize() {
        return ApiResponse.ok(Map.of("status", "synced", "added", commands.synchronize(false)));
    }
}
