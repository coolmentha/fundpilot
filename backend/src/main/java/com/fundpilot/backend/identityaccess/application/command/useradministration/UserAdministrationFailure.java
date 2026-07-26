package com.fundpilot.backend.identityaccess.application.command.useradministration;

public final class UserAdministrationFailure extends RuntimeException {

    private final Code code;

    public UserAdministrationFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        USER_ACCOUNT_INVALID,
        USER_NOT_FOUND,
        ADMIN_FORBIDDEN
    }
}
