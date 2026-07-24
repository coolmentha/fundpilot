package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.market.service.KlineService;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 行情数据查询 Controller(issue #16 + 行情工作台):
 * <ul>
 *   <li>GET 今日行情指标(信号引擎用)</li>
 *   <li>GET 基金 K 线/走势图(行情工作台详情页,日/周/月切换)</li>
 * </ul>
 * refresh 端点已由 {@code AdminMarketDataController} 实现,此处只读查询。
 * 返回 View DTO,不直接暴露 Entity。
 */
@RestController
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketIndicatorProvider marketIndicatorProvider;
    private final KlineService klineService;
    private final MarketRealtimeCache marketRealtimeCache;

    @GetMapping("/api/funds/{fundId}/market-indicators/today")
    public ApiResponse<MarketIndicatorSnapshotView> today(@PathVariable Long fundId) {
        return ApiResponse.ok(marketIndicatorProvider.getIndicators(fundId, Instant.now())
                .map(MarketIndicatorSnapshotView::from).orElse(null));
    }

    /**
     * 基金 K 线/走势图。ETF/指数基金返回跟踪指数 K 线(OHLCV + 成交量),
     * 主动/混合基金返回累计净值走势(折线图)。
     *
     * @param fundId 基金 ID
     * @param period K 线周期:daily(默认)/ weekly / monthly
     */
    @GetMapping("/api/funds/{fundId}/kline")
    public ApiResponse<KlineView> kline(
            @PathVariable Long fundId,
            @RequestParam(name = "period", defaultValue = "daily") String period) {
        return ApiResponse.ok(klineService.getKline(fundId, period));
    }

    /** 基金详情当日分时图，只读后台实时缓存。 */
    @GetMapping("/api/funds/{fundId}/intraday")
    public ApiResponse<FundIntradayView> intraday(@PathVariable Long fundId) {
        return ApiResponse.ok(FundIntradayView.from(marketRealtimeCache.getIntraday(fundId)));
    }
}
