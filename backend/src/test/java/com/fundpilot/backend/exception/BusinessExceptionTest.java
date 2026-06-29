package com.fundpilot.backend.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #4 验收:业务异常基类 {@link BusinessException},携带 {@code code} 与 {@code message}。
 * <p>{@code code} 是机器可读的错误标识(如 {@code NO_VALID_BACKTEST}),
 * {@code message} 是人类可读描述。{@link com.fundpilot.backend.common.ApiResponse#error}
 * 会把它们透传给前端。
 */
class BusinessExceptionTest {

    @Test
    void carriesCodeAndMessage() {
        BusinessException ex = new BusinessException("NO_VALID_BACKTEST", "没有可用的回测结果");

        assertThat(ex.getCode()).isEqualTo("NO_VALID_BACKTEST");
        assertThat(ex.getMessage()).isEqualTo("没有可用的回测结果");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
