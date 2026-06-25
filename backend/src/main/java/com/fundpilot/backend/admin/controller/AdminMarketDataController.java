package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理/调试入口(issue #7):手动触发当日全量行情指标刷新。
 * 供运维或开发在定时任务之外手动重跑(如某批次失败后补数据)。
 */
@RestController
@RequestMapping("/api/admin/market-data")
public class AdminMarketDataController {

    private final MarketDataFetchService marketDataFetchService;

    public AdminMarketDataController(MarketDataFetchService marketDataFetchService) {
        this.marketDataFetchService = marketDataFetchService;
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh() {
        marketDataFetchService.refreshAll();
        return ApiResponse.ok(Map.of("status", "refreshed"));
    }
}
