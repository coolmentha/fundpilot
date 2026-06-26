package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 行情数据查询 Controller(issue #16):GET 今日行情指标。
 * <p>refresh 端点已由 {@code AdminMarketDataController} 实现,此处只读查询。
 */
@RestController
public class MarketDataController {

    private final MarketIndicatorProvider marketIndicatorProvider;

    public MarketDataController(MarketIndicatorProvider marketIndicatorProvider) {
        this.marketIndicatorProvider = marketIndicatorProvider;
    }

    @GetMapping("/api/funds/{fundId}/market-indicators/today")
    public ApiResponse<MarketIndicatorSnapshotEntity> today(@PathVariable Long fundId) {
        return ApiResponse.ok(marketIndicatorProvider.getIndicators(fundId, LocalDate.now()).orElse(null));
    }
}
