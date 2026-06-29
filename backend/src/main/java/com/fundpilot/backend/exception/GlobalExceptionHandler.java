package com.fundpilot.backend.exception;

import com.fundpilot.backend.common.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理,把业务异常转成 {@link ApiResponse#error} 响应。
 * <p>所有业务异常({@link BusinessException} 及其子类 {@link EntityNotFoundException}、
 * {@link IllegalStateTransitionException})统一映射 HTTP 400——业务问题主动抛出,与"代码问题→500"区分。
 * 兜底 {@link Exception} → 500(code=INTERNAL_ERROR),记录原始堆栈便于排查。
 * 业务异常已被显式抛出并携带 code/message,无需再记日志;兜底异常是未预期的,
 * 用 logger 记录原始堆栈便于排查,响应体只返固定文案,不泄露内部细节。
 */
@RestControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MeterRegistry meterRegistry;

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(400).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未预期的异常,转 500 INTERNAL_ERROR", ex);
        meterRegistry.counter("http_server_errors_total", "code", ErrorCode.INTERNAL_ERROR.name()).increment();
        return ResponseEntity.status(500).body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), "服务器内部错误"));
    }
}
