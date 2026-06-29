package com.fundpilot.backend.exception;

import lombok.Getter;

/**
 * 通用业务异常,携带机器可读 {@code code} 与人类可读 {@code message}。
 * <p>{@code GlobalExceptionHandler} 捕获后转成 HTTP 400 响应。
 * 子类 {@link EntityNotFoundException}、{@link IllegalStateTransitionException} 同样映射 400
 * (业务问题统一 400,404 留给框架处理路由不存在)。
 * <p>推荐用 {@link ErrorCode} 构造,避免散落字符串字面量;String 构造器保留供过渡。
 *
 * @see com.fundpilot.backend.common.ApiResponse#error(String, String)
 * @see ErrorCode
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code.name();
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
