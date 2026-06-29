package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.market.service.TradingCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理/调试入口(issue #7):手动触发当日全量行情指标刷新 + 交易日历同步。
 * 供运维或开发在定时任务之外手动重跑(如某批次失败后补数据)。
 */
@RestController
@RequestMapping("/api/admin/market-data")
@RequiredArgsConstructor
public class AdminMarketDataController {

    private final MarketDataFetchService marketDataFetchService;
    private final TradingCalendarSyncService tradingCalendarSyncService;

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh() {
        marketDataFetchService.refreshAll();
        return ApiResponse.ok(Map.of("status", "refreshed"));
    }

    /** 从东方财富同步 A 股交易日历(幂等,可重复调用)。 */
    @PostMapping("/sync-trading-calendar")
    public ApiResponse<Map<String, Object>> syncTradingCalendar() {
        int added = tradingCalendarSyncService.sync();
        return ApiResponse.ok(Map.of("status", "synced", "added", added));
    }
}
