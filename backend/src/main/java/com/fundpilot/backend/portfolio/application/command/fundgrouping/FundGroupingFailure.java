package com.fundpilot.backend.portfolio.application.command.fundgrouping;

public final class FundGroupingFailure extends RuntimeException {
    private final Code code;

    public FundGroupingFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        FUND_GROUP_NAME_INVALID,
        FUND_GROUP_NAME_DUPLICATE,
        FUND_GROUP_NOT_FOUND,
        PORTFOLIO_FUND_NOT_FOUND
    }
}
