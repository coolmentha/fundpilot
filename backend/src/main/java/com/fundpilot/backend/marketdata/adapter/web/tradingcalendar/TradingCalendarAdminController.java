package com.fundpilot.backend.marketdata.adapter.web.tradingcalendar;

import com.fundpilot.backend.marketdata.application.command.tradingcalendar.TradingCalendarCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/market-data")
@RequiredArgsConstructor
public class TradingCalendarAdminController {
    private final TradingCalendarCommandHandler commands;

    @PostMapping("/sync-trading-calendar")
    public ApiResponse<Map<String, Object>> synchronize() {
        return ApiResponse.ok(Map.of("status", "synced", "added", commands.synchronize(false)));
    }
}
