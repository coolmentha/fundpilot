package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 行情数据查询 Controller(issue #16):GET 今日行情指标。
 * <p>refresh 端点已由 {@code AdminMarketDataController} 实现,此处只读查询。
 * 返回 {@link MarketIndicatorSnapshotView} DTO,不直接暴露 Entity。
 */
@RestController
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketIndicatorProvider marketIndicatorProvider;

    @GetMapping("/api/funds/{fundId}/market-indicators/today")
    public ApiResponse<MarketIndicatorSnapshotView> today(@PathVariable Long fundId) {
        return ApiResponse.ok(marketIndicatorProvider.getIndicators(fundId, Instant.now())
                .map(MarketIndicatorSnapshotView::from).orElse(null));
    }
}
