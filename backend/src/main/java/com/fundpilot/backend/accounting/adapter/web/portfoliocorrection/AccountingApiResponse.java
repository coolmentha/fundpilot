package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

record AccountingApiResponse<T>(boolean success, T data, String code, String message) {
    static <T> AccountingApiResponse<T> ok(T data) {
        return new AccountingApiResponse<>(true, data, null, null);
    }

    static AccountingApiResponse<Void> error(String code, String message) {
        return new AccountingApiResponse<>(false, null, code, message);
    }
}
