package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@Tag(name = "组合持仓修正接口", description = "组合持仓修正相关操作")
@RestController
@RequestMapping("/api/portfolio-funds")
@RequiredArgsConstructor
public class PortfolioCorrectionController {
    private final PortfolioCorrectionCommandHandler commands;

    @PostMapping("/{portfolioFundId}/void")
    @Operation(summary = "作废组合基金持仓")
    public ApiResponse<VoidPortfolioFundView> voidPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody(required = false) VoidPortfolioFundRequest request) {
        var result = commands.voidPortfolioFund(ownerId, portfolioFundId,
                request == null ? null : request.reason(),
                request != null && request.confirmed());
        return ApiResponse.ok(new VoidPortfolioFundView(
                result.portfolioFundId(), result.changed(), result.voidedAt(),
                result.voidedBy(), result.voidReason()));
    }

    @PutMapping("/{portfolioFundId}/cost-basis")
    @Operation(summary = "更新组合基金成本基准")
    public ApiResponse<CostBasisView> correctCostPerShare(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody(required = false) CostBasisRequest request) {
        var result = commands.correctCostPerShare(ownerId, portfolioFundId,
                request == null ? null : request.costPerShare());
        return ApiResponse.ok(new CostBasisView(result.portfolioFundId(), result.costPerShare()));
    }

    @Schema(description = "作废组合基金持仓请求")
    public record VoidPortfolioFundRequest(@Schema(description = "作废原因", example = "误操作买入，申请作废") String reason,
                                           @Schema(description = "是否确认作废，true 确认执行作废 / false 未确认不执行", example = "true") boolean confirmed) {
    }

    @Schema(description = "组合基金持仓作废视图")
    public record VoidPortfolioFundView(@Schema(description = "组合基金持仓ID", example = "1001") long portfolioFundId,
                                        @Schema(description = "持仓是否已变更，true 已变更 / false 未变更", example = "true") boolean changed,
                                        @Schema(description = "作废时间", example = "2026-08-21T08:00:00Z") Instant voidedAt,
                                        @Schema(description = "作废操作人用户ID", example = "10001") Long voidedBy,
                                        @Schema(description = "作废原因", example = "误操作买入，申请作废") String voidReason) {
    }

    @Schema(description = "成本基准修正请求")
    public record CostBasisRequest(@Schema(description = "单位成本基准", example = "3.40") BigDecimal costPerShare) {
    }

    @Schema(description = "成本基准修正结果")
    public record CostBasisView(@Schema(description = "组合基金ID", example = "41") long portfolioFundId,
                                @Schema(description = "单位成本基准", example = "3.40") BigDecimal costPerShare) {
    }
}

