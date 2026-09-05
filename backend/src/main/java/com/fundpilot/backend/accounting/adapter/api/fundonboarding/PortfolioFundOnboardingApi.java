package com.fundpilot.backend.accounting.adapter.api.fundonboarding;

import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingCommandHandler;
import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingFailure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Accounting 对开户与期初持仓的公开入站契约。 */
@Component
@RequiredArgsConstructor
public class PortfolioFundOnboardingApi {
    private final PortfolioFundOnboardingCommandHandler commands;

    public OnboardingResult onboard(OnboardPortfolioFund request) {
        try {
            var result = commands.onboard(request.legacyFundId(), request.ownerId(), request.fundProductId(),
                    request.positionWarningEnabled(), request.positionWarningRatio(),
                    request.initialHoldingShares(), request.costPerShare(), request.openedAt(), request.groupNames());
            return new OnboardingResult(result.portfolioFundId(), result.initialTransactionId());
        } catch (PortfolioFundOnboardingFailure failure) {
            throw new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }
    }

    public record OnboardPortfolioFund(Long legacyFundId, long ownerId, long fundProductId,
                                       boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                       BigDecimal initialHoldingShares, BigDecimal costPerShare,
                                       Instant openedAt, List<String> groupNames) {
        public OnboardPortfolioFund(Long legacyFundId, long ownerId, long fundProductId,
                                    boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                    BigDecimal initialHoldingShares, BigDecimal costPerShare,
                                    Instant openedAt) {
            this(legacyFundId, ownerId, fundProductId, positionWarningEnabled, positionWarningRatio,
                    initialHoldingShares, costPerShare, openedAt, List.of());
        }
    }

    public record OnboardingResult(long portfolioFundId, Long initialTransactionId) {
    }

    public static final class Failure extends RuntimeException {
        private final Code code;

        private Failure(Code code, String message) {
            super(message);
            this.code = code;
        }

        public Code code() {
            return code;
        }
    }

    public enum Code {
        PRODUCT_NOT_FOUND,
        PORTFOLIO_FUND_ALREADY_TRACKED,
        POSITION_WARNING_INVALID,
        INITIAL_HOLDING_SHARES_INVALID,
        COST_PER_SHARE_INVALID,
        OPENED_AT_IN_FUTURE,
        NAV_UNAVAILABLE,
        FUND_GROUP_NAME_INVALID,
        FUND_GROUP_NAME_DUPLICATE,
        FUND_GROUP_NOT_FOUND,
        PORTFOLIO_FUND_NOT_FOUND
    }
}
