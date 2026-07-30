package com.fundpilot.backend.discipline.application.command.adviceresponse;

/** 建议回应的稳定错误。 */
public class AdviceResponseFailure extends RuntimeException {
    private final Code code;

    public AdviceResponseFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }

    public enum Code { ADVICE_NOT_FOUND, ADVICE_IGNORED, ADVICE_NOT_ACTIONABLE, VALUE_REQUIRED, ALREADY_RESPONDED }
}
