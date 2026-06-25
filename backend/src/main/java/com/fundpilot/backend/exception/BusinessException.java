package com.fundpilot.backend.exception;

import lombok.Getter;

/**
 * 通用业务异常,携带机器可读 {@code code} 与人类可读 {@code message}。
 * <p>{@code GlobalExceptionHandler} 捕获后转成 HTTP 400 响应。
 * 子类 {@link EntityNotFoundException}、{@link IllegalStateTransitionException}
 * 分别映射 404、409。
 *
 * @see com.fundpilot.backend.common.ApiResponse#error(String, String)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
