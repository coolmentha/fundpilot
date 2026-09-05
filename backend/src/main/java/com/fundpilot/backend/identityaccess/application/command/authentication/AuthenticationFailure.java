package com.fundpilot.backend.identityaccess.application.command.authentication;

public final class AuthenticationFailure extends RuntimeException {

    private final Code code;
    private final long retryAfterSeconds;

    public AuthenticationFailure(Code code, String message) {
        this(code, message, 0);
    }

    public AuthenticationFailure(Code code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public Code code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Code {
        ADMIN_UNAUTHORIZED,
        ADMIN_FORBIDDEN,
        ADMIN_AUTH_NOT_CONFIGURED,
        AUTH_RATE_LIMITED,
        PASSWORD_POLICY_VIOLATION,
        CURRENT_PASSWORD_INVALID
    }
}
