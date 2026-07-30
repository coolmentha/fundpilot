package com.fundpilot.backend.importing.adapter.web.importsession;

record ImportingApiResponse<T>(boolean success, String code, String message, T data) {
    static <T> ImportingApiResponse<T> ok(T data) { return new ImportingApiResponse<>(true, "OK", null, data); }
    static <T> ImportingApiResponse<T> error(String code, String message) {
        return new ImportingApiResponse<>(false, code, message, null);
    }
}
