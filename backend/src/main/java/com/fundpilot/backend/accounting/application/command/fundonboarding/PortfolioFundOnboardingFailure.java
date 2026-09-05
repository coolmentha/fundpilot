package com.fundpilot.backend.accounting.application.command.fundonboarding;

/** 开户及期初持仓的稳定业务错误。 */
public class PortfolioFundOnboardingFailure extends RuntimeException {
    private final Code code;

    public PortfolioFundOnboardingFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
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
