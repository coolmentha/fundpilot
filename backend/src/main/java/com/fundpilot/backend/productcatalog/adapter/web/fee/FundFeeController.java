package com.fundpilot.backend.productcatalog.adapter.web.fee;

import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeCommandHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{fundCode}/fees")
@RequiredArgsConstructor
public class FundFeeController {
    private final FundFeeCommandHandler commands;

    @GetMapping
    public ApiResponse<FeeResponse> get(@PathVariable String fundCode) {
        return new ApiResponse<>(true, commands.findOrRefresh(fundCode).map(FeeResponse::from).orElse(null));
    }

    public record ApiResponse<T>(boolean success, T data) {}

    public record FeeResponse(BigDecimal purchaseRate, BigDecimal discountRate,
                              BigDecimal salesServiceFee, List<RedemptionTierView> redemptionLadder,
                              Instant fetchedAt) {
        static FeeResponse from(FundFeeCommandHandler.FeeResult result) {
            return new FeeResponse(result.purchaseRate(), result.discountRate(), result.salesServiceFee(),
                    result.redemptionTiers().stream().map(tier ->
                            new RedemptionTierView(tier.maxDays(), tier.rate())).toList(), result.fetchedAt());
        }
    }
    public record RedemptionTierView(Integer maxDays, BigDecimal rate) {}
}
