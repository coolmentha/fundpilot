package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

/** 交易确认与撤销的稳定业务错误。 */
public class TransactionConfirmationFailure extends RuntimeException {

    private final Code code;

    public TransactionConfirmationFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        TRANSACTION_NOT_FOUND,
        TRANSACTION_ALREADY_CONFIRMED,
        TRANSACTION_ALREADY_CANCELLED,
        ILLEGAL_STATE_TRANSITION,
        NAV_UNAVAILABLE,
        TRANSACTION_INPUT_REQUIRED,
        INSUFFICIENT_HOLDING_SHARES,
        INSUFFICIENT_LOTS,
        PORTFOLIO_FUND_NOT_TRADABLE,
        AMOUNT_TOO_SMALL
    }
}
