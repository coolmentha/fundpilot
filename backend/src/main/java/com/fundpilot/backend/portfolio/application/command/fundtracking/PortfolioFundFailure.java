package com.fundpilot.backend.portfolio.application.command.fundtracking;

public final class PortfolioFundFailure extends RuntimeException {
    private final Code code;

    public PortfolioFundFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        PRODUCT_NOT_FOUND,
        PORTFOLIO_FUND_NOT_FOUND,
        PORTFOLIO_FUND_ALREADY_TRACKED,
        POSITION_WARNING_INVALID,
        PORTFOLIO_FUND_VOIDED,
        VOID_REASON_REQUIRED
    }
}
