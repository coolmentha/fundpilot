package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.portfolio.application.query.fundtracking.PortfolioFundViewQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "组合基金接口", description = "组合基金查询相关操作")
@RestController
@RequestMapping("/api/portfolio-funds")
@RequiredArgsConstructor
public class PortfolioFundController {
    private final PortfolioFundViewQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询当前操作主体的组合基金")
    public ApiResponse<List<PortfolioFundView>> list(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.findByOwner(ownerId).stream().map(PortfolioFundView::from).toList());
    }

    @GetMapping("/{portfolioFundId}")
    @Operation(summary = "查询组合基金详情")
    public ApiResponse<PortfolioFundView> get(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId) {
        return queries.findOwned(ownerId, portfolioFundId)
                .map(PortfolioFundView::from)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new NotFound(portfolioFundId));
    }

    static final class NotFound extends RuntimeException {
        private NotFound(long portfolioFundId) {
            super("组合基金不存在: " + portfolioFundId);
        }

        Code code() {
            return Code.PORTFOLIO_FUND_NOT_FOUND;
        }
    }

    enum Code {
        PORTFOLIO_FUND_NOT_FOUND
    }

    @Schema(description = "组合基金视图")
    public record PortfolioFundView(long portfolioFundId, long fundProductId, String fundCode, String fundName,
                                    String productType, String investmentTarget, String benchmarkIndexCode,
                                    String validity, boolean positionWarningEnabled,
                                    BigDecimal positionWarningRatio, List<GroupView> groups) {
        static PortfolioFundView from(PortfolioFundViewQueryHandler.ViewResult result) {
            return new PortfolioFundView(result.portfolioFundId(), result.fundProductId(), result.fundCode(),
                    result.fundName(), result.productType(), result.investmentTarget(), result.benchmarkIndexCode(),
                    result.validity(), result.positionWarningEnabled(), result.positionWarningRatio(),
                    result.groups().stream().map(group -> new GroupView(group.id(), group.name())).toList());
        }
    }

    @Schema(description = "组合基金分组")
    public record GroupView(long id, String name) {
    }
}
