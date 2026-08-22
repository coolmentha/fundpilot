package com.fundpilot.backend.productcatalog.adapter.web.fee;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeCommandHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "基金费率接口", description = "基金费率相关操作")
@RestController
@RequestMapping("/api/products/{fundCode}/fees")
@RequiredArgsConstructor
public class FundFeeController {
    private final FundFeeCommandHandler commands;

    @Operation(summary = "查询基金费率,缓存未命中时自动刷新")
    @GetMapping
    public ApiResponse<FeeResponse> get(@PathVariable String fundCode) {
        return ApiResponse.ok(commands.findOrRefresh(fundCode).map(FeeResponse::from).orElse(null));
    }

    @Schema(description = "基金费率视图")
    public record FeeResponse(
            @Schema(description = "申购费率", example = "0.0015") BigDecimal purchaseRate,
            @Schema(description = "申购费率折扣率,例如 0.1 表示一折", example = "0.1") BigDecimal discountRate,
            @Schema(description = "销售服务费率", example = "0.004") BigDecimal salesServiceFee,
            @Schema(description = "赎回费阶梯列表,按持有天数分档") List<RedemptionTierView> redemptionLadder,
            @Schema(description = "费率数据获取时间", example = "2026-08-21T09:30:00Z") Instant fetchedAt) {
        static FeeResponse from(FundFeeCommandHandler.FeeResult result) {
            return new FeeResponse(result.purchaseRate(), result.discountRate(), result.salesServiceFee(),
                    result.redemptionTiers().stream().map(tier ->
                            new RedemptionTierView(tier.maxDays(), tier.rate())).toList(), result.fetchedAt());
        }
    }
    @Schema(description = "赎回费阶梯视图")
    public record RedemptionTierView(
            @Schema(description = "该档费率适用的最大持有天数", example = "7") Integer maxDays,
            @Schema(description = "该档赎回费率", example = "0.015") BigDecimal rate) {}
}
