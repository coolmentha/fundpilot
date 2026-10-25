package com.fundpilot.backend.marketdata.adapter.web.openapi;

import com.fundpilot.backend.marketdata.adapter.web.realtimevaluation.IndexRealtimeView;
import com.fundpilot.backend.marketdata.adapter.web.realtimevaluation.MarketBreadthView;
import com.fundpilot.backend.marketdata.adapter.web.realtimevaluation.MarketStatusView;
import com.fundpilot.backend.marketdata.adapter.web.realtimevaluation.MarketVolumePriceView;
import com.fundpilot.backend.marketdata.adapter.web.realtimevaluation.SectorView;
import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeMarketOverviewQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Open API v1 行情只读资源。 */
@Tag(name = "Open API 行情", description = "指数、市场宽度、量价与板块")
@RestController
@RequestMapping("/api/open/v1/market")
@RequiredArgsConstructor
public class OpenApiMarketController {

    private final RealtimeMarketOverviewQueryHandler queries;

    @GetMapping("/status")
    @Operation(summary = "查询 A 股交易状态")
    public ApiResponse<MarketStatusView> status() {
        return ApiResponse.ok(MarketStatusView.from(queries.findStatus()));
    }

    @GetMapping("/indices")
    @Operation(summary = "查询用户关注指数实时行情")
    public ApiResponse<List<IndexRealtimeView>> indices() {
        return ApiResponse.ok(queries.findCurrentActorIndices().stream().map(IndexRealtimeView::from).toList());
    }

    @GetMapping("/breadth")
    @Operation(summary = "查询市场涨跌家数")
    public ApiResponse<MarketBreadthView> breadth() {
        return ApiResponse.ok(MarketBreadthView.from(queries.findBreadth()));
    }

    @GetMapping("/sectors")
    @Operation(summary = "查询行业板块涨跌")
    public ApiResponse<List<SectorView>> sectors() {
        return ApiResponse.ok(queries.findSectors().stream().map(SectorView::from).toList());
    }

    @GetMapping("/volume-price")
    @Operation(summary = "查询上证指数量价状态")
    public ApiResponse<MarketVolumePriceView> volumePrice() {
        return ApiResponse.ok(MarketVolumePriceView.from(queries.findVolumePrice()));
    }
}
