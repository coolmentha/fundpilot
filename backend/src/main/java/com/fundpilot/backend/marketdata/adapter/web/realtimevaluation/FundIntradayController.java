package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeValuationQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "分时估值接口", description = "分时估值相关操作")
@RestController
@RequiredArgsConstructor
public class FundIntradayController {
    private final RealtimeValuationQueryHandler queries;

    @Operation(summary = "查询基金分时估值")
    @GetMapping("/api/funds/{fundId}/intraday")
    public ApiResponse<FundIntradayView> intraday(@PathVariable long fundId) {
        return ApiResponse.ok(queries.findIntraday(fundId).map(FundIntradayView::from).orElse(null));
    }

    @Operation(summary = "查询组合基金分时估值")
    @GetMapping("/api/portfolio-funds/{portfolioFundId}/intraday")
    public ApiResponse<FundIntradayView> portfolioFundIntraday(@PathVariable long portfolioFundId) {
        return ApiResponse.ok(queries.findIntradayForPortfolioFund(portfolioFundId)
                .map(FundIntradayView::from).orElse(null));
    }

    @Schema(description = "基金分时估值视图")
    public record FundIntradayView(
            @Schema(description = "估值日期", example = "2026-08-21") String estimateDate,
            @Schema(description = "基准单位净值,即最近一个交易日收盘净值", example = "1.5230") BigDecimal baseNav,
            @Schema(description = "盘中分时估值点列表") List<Point> points,
            @Schema(description = "A股交易时段列表") List<TradingSession> tradingSessions) {
        static FundIntradayView from(RealtimeValuationQueryHandler.IntradayResult value) {
            return new FundIntradayView(value.estimateDate(), value.baseNav(), value.points().stream()
                    .map(point -> new Point(point.time(), point.nav())).toList(), value.tradingSessions().stream()
                    .map(session -> new TradingSession(session.start(), session.end())).toList());
        }
    }

    @Schema(description = "分时估值点视图")
    public record Point(
            @Schema(description = "估值时间点", example = "10:30") String time,
            @Schema(description = "该时间点的估算净值", example = "1.5310") BigDecimal nav) {}

    @Schema(description = "交易时段视图")
    public record TradingSession(
            @Schema(description = "时段开始时间", example = "09:30") String start,
            @Schema(description = "时段结束时间", example = "11:30") String end) {}
}
