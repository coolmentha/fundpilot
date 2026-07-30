package com.fundpilot.backend.productcatalog.application.command.feerefresh;

public final class FundFeeFailure extends RuntimeException {
    private final Code code;

    public FundFeeFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }

    public enum Code { FUND_FEE_INPUT_INVALID }
}
