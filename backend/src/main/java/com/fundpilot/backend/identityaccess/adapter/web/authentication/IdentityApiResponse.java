package com.fundpilot.backend.identityaccess.adapter.web.authentication;

public record IdentityApiResponse<T>(boolean success, T data, String code, String message) {

    public static <T> IdentityApiResponse<T> ok(T data) {
        return new IdentityApiResponse<>(true, data, null, null);
    }

    public static IdentityApiResponse<Void> error(String code, String message) {
        return new IdentityApiResponse<>(false, null, code, message);
    }
}
