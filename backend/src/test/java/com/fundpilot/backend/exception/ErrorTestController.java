package com.fundpilot.backend.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * issue #4 测试专用 Controller:只为 {@link GlobalExceptionHandler} 切片测试抛异常,
 * 不进 main 源集,端点名固定 {@code /test/errors/*}。验收要求"MockMvc 调一个临时空
 * Controller 验证状态码 + 响应体格式"即此。
 */
@RestController
@RequestMapping("/test/errors")
class ErrorTestController {

    @GetMapping("/not-found")
    void notFound() {
        throw new EntityNotFoundException("Fund", 1L);
    }

    @GetMapping("/illegal-transition")
    void illegalTransition() {
        throw new IllegalStateTransitionException("PENDING", "CONFIRMED");
    }

    @GetMapping("/business")
    void business() {
        throw new BusinessException("MIN_HOLD_DAYS_NOT_MET", "持有期不满 5 个交易日");
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("boom");
    }
}
