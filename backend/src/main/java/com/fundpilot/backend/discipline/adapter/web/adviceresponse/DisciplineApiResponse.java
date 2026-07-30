package com.fundpilot.backend.discipline.adapter.web.adviceresponse;

/** Discipline Web 入口的稳定响应信封。 */
record DisciplineApiResponse<T>(boolean success, T data, String code, String message) {
    static <T> DisciplineApiResponse<T> ok(T data) {
        return new DisciplineApiResponse<>(true, data, null, null);
    }

    static <T> DisciplineApiResponse<T> error(String code, String message) {
        return new DisciplineApiResponse<>(false, null, code, message);
    }
}
