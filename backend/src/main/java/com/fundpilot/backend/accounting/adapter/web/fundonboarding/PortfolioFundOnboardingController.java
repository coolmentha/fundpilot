package com.fundpilot.backend.accounting.adapter.web.fundonboarding;

import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "组合基金开户接口", description = "组合基金加入与初始持仓登记")
@RestController
@RequestMapping("/api/portfolio-funds")
@RequiredArgsConstructor
public class PortfolioFundOnboardingController {
    private static final BigDecimal DEFAULT_POSITION_WARNING_RATIO = new BigDecimal("0.30");

    private final PortfolioFundOnboardingCommandHandler onboarding;

    @PostMapping
    @Operation(summary = "将产品加入当前操作主体的组合")
    public ApiResponse<OnboardingView> create(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @RequestBody CreateRequest request) {
        long fundProductId = request.fundProductId == null ? 0L : request.fundProductId;
        var result = onboarding.onboard(null, ownerId, fundProductId,
                request.positionWarningEnabled == null || request.positionWarningEnabled,
                request.positionWarningRatio == null ? DEFAULT_POSITION_WARNING_RATIO : request.positionWarningRatio,
                request.initialHoldingShares, request.costPerShare, request.openedAt,
                request.groupNames == null ? List.of() : request.groupNames);
        return ApiResponse.ok(new OnboardingView(result.portfolioFundId(), fundProductId,
                result.initialTransactionId()));
    }

    @Schema(description = "组合基金开户请求")
    public record CreateRequest(Long fundProductId, Boolean positionWarningEnabled,
                                BigDecimal positionWarningRatio, BigDecimal initialHoldingShares,
                                BigDecimal costPerShare, Instant openedAt, List<String> groupNames) {
    }

    @Schema(description = "组合基金开户结果")
    public record OnboardingView(long portfolioFundId, long fundProductId, Long initialTransactionId) {
    }
}
