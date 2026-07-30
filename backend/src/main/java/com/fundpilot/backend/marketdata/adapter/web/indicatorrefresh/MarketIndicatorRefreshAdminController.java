package com.fundpilot.backend.marketdata.adapter.web.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/market-data")
@RequiredArgsConstructor
public class MarketIndicatorRefreshAdminController {
    private final MarketIndicatorRefreshCommandHandler commands;

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh() {
        commands.refreshAll();
        return ApiResponse.ok(Map.of("status", "refreshed"));
    }
}
