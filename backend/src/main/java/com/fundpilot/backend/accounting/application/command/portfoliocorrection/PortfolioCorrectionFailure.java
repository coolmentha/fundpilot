package com.fundpilot.backend.accounting.application.command.portfoliocorrection;

public final class PortfolioCorrectionFailure extends RuntimeException {
    private final Code code;

    public PortfolioCorrectionFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        VOID_CONFIRMATION_REQUIRED,
        VOID_REASON_REQUIRED,
        PORTFOLIO_FUND_NOT_FOUND,
        PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS,
        PORTFOLIO_FUND_CORRECTION_CONFLICT
    }
}
