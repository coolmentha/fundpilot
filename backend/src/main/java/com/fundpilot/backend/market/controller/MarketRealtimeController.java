package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 行情实时数据查询 Controller(行情工作台)。
 *
 * <p>五个只读接口,前端按不同频率轮询:
 * <ul>
 *   <li>{@code GET /indices/realtime} 指数实时(5-10s 轮询)</li>
 *   <li>{@code GET /breadth} 沪深京股票涨跌家数(5-10s 轮询)</li>
 *   <li>{@code GET /funds/estimates?codes=xxx} 基金估值(10s 轮询)</li>
 *   <li>{@code GET /sectors} 板块涨跌(30s 轮询)</li>
 *   <li>{@code GET /money-flow} 北向资金(30s 轮询)</li>
 * </ul>
 * 全部读 {@link MarketRealtimeCache} 内存,零外部请求,无降级异常。
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketRealtimeController {

    private final MarketRealtimeCache cache;

    /** 用户关注指数的实时行情(前端 5-10s 轮询)。 */
    @GetMapping("/indices/realtime")
    public ApiResponse<List<IndexRealtimeView>> indices() {
        return ApiResponse.ok(cache.getIndices().stream()
                .map(IndexRealtimeView::from).toList());
    }

    /** 沪深京上涨、下跌股票家数。 */
    @GetMapping("/breadth")
    public ApiResponse<MarketBreadthView> breadth() {
        return ApiResponse.ok(MarketBreadthView.from(cache.getBreadth()));
    }

    /**
     * 批量基金估值(前端 10s 轮询)。
     * @param codes 基金代码逗号分隔(如 "000001,000002")
     * @return code → 估值视图;缓存未命中的 code 不在 map 中(前端降级显示「-」)
     */
    @GetMapping("/funds/estimates")
    public ApiResponse<Map<String, FundEstimateView>> estimates(@RequestParam("codes") List<String> codes) {
        return ApiResponse.ok(FundEstimateView.from(cache.getEstimates(codes)));
    }

    /** 行业板块涨跌排行(前端 30s 轮询)。 */
    @GetMapping("/sectors")
    public ApiResponse<List<SectorView>> sectors() {
        return ApiResponse.ok(cache.getSectors().stream()
                .map(SectorView::from).toList());
    }

    /** 北向资金净流入(前端 30s 轮询)。 */
    @GetMapping("/money-flow")
    public ApiResponse<MoneyFlowView> moneyFlow() {
        return ApiResponse.ok(MoneyFlowView.from(cache.getMoneyFlow()));
    }
}
