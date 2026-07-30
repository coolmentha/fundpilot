package com.fundpilot.backend.insights.adapter.web.portfolioreturn;

record InsightsApiResponse<T>(boolean success, T data, String code, String message) {
    static <T> InsightsApiResponse<T> ok(T data) { return new InsightsApiResponse<>(true, data, null, null); }
}
