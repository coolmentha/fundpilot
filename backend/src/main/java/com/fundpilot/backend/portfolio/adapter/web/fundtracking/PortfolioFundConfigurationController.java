package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingCommandHandler;
import com.fundpilot.backend.portfolio.application.command.fundtracking.PortfolioFundCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "组合基金配置接口", description = "组合基金提醒与分组配置")
@RestController
@RequestMapping("/api/portfolio-funds")
@RequiredArgsConstructor
public class PortfolioFundConfigurationController {
    private final PortfolioFundCommandHandler portfolioFunds;
    private final FundGroupingCommandHandler groups;

    @PutMapping("/{portfolioFundId}/position-warning")
    @Operation(summary = "更新组合基金仓位提醒")
    public ApiResponse<PositionWarningView> updateWarning(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody(required = false) PositionWarningRequest request) {
        PositionWarningRequest body = request == null ? new PositionWarningRequest(null, null) : request;
        var result = portfolioFunds.configureWarning(ownerId, portfolioFundId, body.enabled(), body.ratio());
        return ApiResponse.ok(new PositionWarningView(result.id(), result.positionWarningEnabled(),
                result.positionWarningRatio()));
    }

    @PutMapping("/{portfolioFundId}/groups")
    @Operation(summary = "替换组合基金分组")
    public ApiResponse<GroupsView> replaceGroups(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody(required = false) GroupsRequest request) {
        List<String> names = request == null ? null : request.groupNames();
        List<FundGroupingCommandHandler.GroupResult> result = groups.assignByNames(
                ownerId, portfolioFundId, names);
        return ApiResponse.ok(new GroupsView(portfolioFundId,
                result.stream().map(GroupView::from).toList()));
    }

    @Schema(description = "仓位提醒请求")
    public record PositionWarningRequest(Boolean enabled, BigDecimal ratio) {
    }

    @Schema(description = "仓位提醒结果")
    public record PositionWarningView(long portfolioFundId, boolean positionWarningEnabled,
                                      BigDecimal positionWarningRatio) {
    }

    @Schema(description = "分组替换请求")
    public record GroupsRequest(List<String> groupNames) {
    }

    @Schema(description = "分组替换结果")
    public record GroupsView(long portfolioFundId, List<GroupView> groups) {
    }

    @Schema(description = "组合基金分组")
    public record GroupView(long id, String name) {
        private static GroupView from(FundGroupingCommandHandler.GroupResult result) {
            return new GroupView(result.id(), result.name());
        }
    }
}
