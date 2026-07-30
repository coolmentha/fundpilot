package com.fundpilot.backend.accounting.application.command.transactionledger;

/** 账目录入的稳定业务错误。 */
public class TransactionLedgerFailure extends RuntimeException {

    private final Code code;

    public TransactionLedgerFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        PORTFOLIO_FUND_NOT_FOUND,
        PORTFOLIO_FUND_NOT_TRADABLE,
        TRANSACTION_NOT_FOUND,
        TRANSACTION_ALREADY_CONFIRMED,
        TRANSACTION_ALREADY_CANCELLED,
        TRANSACTION_INPUT_REQUIRED,
        ILLEGAL_STATE_TRANSITION,
        INSUFFICIENT_HOLDING_SHARES,
        ADVICE_ALREADY_RESPONDED,
        INVESTMENT_PLAN_ALREADY_EXECUTED
    }
}
