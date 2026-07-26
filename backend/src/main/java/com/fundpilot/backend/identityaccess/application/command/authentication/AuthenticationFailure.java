package com.fundpilot.backend.identityaccess.application.command.authentication;

public final class AuthenticationFailure extends RuntimeException {

    private final Code code;

    public AuthenticationFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        ADMIN_UNAUTHORIZED,
        ADMIN_FORBIDDEN,
        ADMIN_AUTH_NOT_CONFIGURED
    }
}
