package com.fundpilot.backend.portfolio.adapter.web.openapi;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import com.fundpilot.backend.portfolio.application.query.fundgrouping.FundGroupingQueryHandler;
import com.fundpilot.backend.portfolio.application.query.fundtracking.PortfolioFundQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Open API v1 组合基金只读资源。 */
@Tag(name = "Open API 基金", description = "当前用户的组合基金与分组")
@RestController
@RequestMapping("/api/open/v1/portfolio-funds")
@RequiredArgsConstructor
public class OpenApiPortfolioController {

    private final PortfolioFundQueryHandler funds;
    private final FundGroupingQueryHandler groups;

    @GetMapping
    @Operation(summary = "查询当前用户的组合基金")
    public ApiResponse<List<FundView>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(funds.findByOwner(ownerId).stream()
                .map(fund -> view(fund, groups.memberships(ownerId))).toList());
    }

    @GetMapping("/{portfolioFundId}")
    @Operation(summary = "查询当前用户的单只组合基金")
    public ApiResponse<FundView> get(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long portfolioFundId) {
        var fund = funds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "组合基金不存在: " + portfolioFundId));
        return ApiResponse.ok(view(fund, groups.memberships(ownerId)));
    }

    private static FundView view(PortfolioFundQueryHandler.PortfolioFundResult fund,
                                 List<FundGroupingQueryHandler.GroupMembershipResult> memberships) {
        return new FundView(fund.id(), fund.fundProductId(), fund.legacyFundId(), fund.validity(),
                fund.positionWarningEnabled(), fund.positionWarningRatio(), memberships.stream()
                .filter(group -> group.portfolioFundId() == fund.id())
                .map(group -> new GroupView(group.groupId(), group.groupName())).toList());
    }

    public record FundView(long id, long fundProductId, Long legacyFundId, String validity,
                           boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                           List<GroupView> groups) {
    }
    public record GroupView(long id, String name) {
    }
}
