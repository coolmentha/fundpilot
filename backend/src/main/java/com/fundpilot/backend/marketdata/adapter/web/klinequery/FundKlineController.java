package com.fundpilot.backend.marketdata.adapter.web.klinequery;

import com.fundpilot.backend.marketdata.application.query.klinequery.KlineQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "指数K线接口", description = "指数K线相关操作")
@RestController
@RequiredArgsConstructor
public class FundKlineController {
    private final KlineQueryHandler queries;

    @Operation(summary = "查询基金K线")
    @GetMapping("/api/funds/{fundId}/kline")
    public ApiResponse<KlineView> kline(@PathVariable long fundId,
                                        @RequestParam(name = "period", defaultValue = "daily") String period) {
        return ApiResponse.ok(KlineView.from(queries.getKline(fundId, period)));
    }

    @Operation(summary = "查询组合基金K线")
    @GetMapping("/api/portfolio-funds/{portfolioFundId}/kline")
    public ApiResponse<KlineView> portfolioFundKline(@PathVariable long portfolioFundId,
                                                     @RequestParam(name = "period", defaultValue = "daily") String period) {
        return ApiResponse.ok(KlineView.from(queries.getKlineForPortfolioFund(portfolioFundId, period)));
    }

    @Schema(description = "K线数据视图")
    public record KlineView(
            @Schema(description = "图表类型", example = "daily") String chartType,
            @Schema(description = "基准指数代码", example = "000300") String benchmark,
            @Schema(description = "K线柱列表,按时间升序排列") List<Bar> bars) {
        static KlineView from(KlineQueryHandler.Kline value) {
            return new KlineView(value.chartType(), value.benchmark(), value.bars().stream()
                    .map(bar -> new Bar(bar.date(), bar.open(), bar.close(), bar.high(), bar.low(), bar.volume()))
                    .toList());
        }
    }

    @Schema(description = "K线柱视图")
    public record Bar(
            @Schema(description = "交易时间", example = "2026-08-21T00:00:00Z") Instant date,
            @Schema(description = "开盘价", example = "3892.50") BigDecimal open,
            @Schema(description = "收盘价", example = "3910.20") BigDecimal close,
            @Schema(description = "最高价", example = "3925.00") BigDecimal high,
            @Schema(description = "最低价", example = "3888.10") BigDecimal low,
            @Schema(description = "成交量", example = "123456789") long volume) {}
}
