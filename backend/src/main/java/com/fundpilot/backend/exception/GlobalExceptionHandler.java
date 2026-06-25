package com.fundpilot.backend.exception;

import com.fundpilot.backend.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理,把业务异常转成 {@link ApiResponse#error} 响应。
 * <p>{@link EntityNotFoundException} → 404、{@link IllegalStateTransitionException} → 409、
 * {@link BusinessException} → 400、兜底 {@link Exception} → 500(code=INTERNAL_ERROR)。
 * 业务异常已被显式抛出并携带 code/message,无需再记日志;兜底异常是未预期的,
 * 用 logger 记录原始堆栈便于排查,响应体只返固定文案,不泄露内部细节。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ResponseEntity<ApiResponse<Void>> handleIllegalStateTransition(IllegalStateTransitionException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(400).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未预期的异常,转 500 INTERNAL_ERROR", ex);
        return ResponseEntity.status(500).body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
