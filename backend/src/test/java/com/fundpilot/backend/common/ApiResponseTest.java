package com.fundpilot.backend.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #4 验收:ApiResponse 补 {@code error(code, message)} 静态工厂方法。
 * <p>错误响应 {@code success=false}、{@code data=null},并把 {@code code} 与 {@code message}
 * 透传给调用方,供前端按 code 做分支、按 message 做提示。
 */
class ApiResponseTest {

    @Test
    void errorCarriesCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.error("NO_VALID_BACKTEST", "没有可用的回测结果");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.code()).isEqualTo("NO_VALID_BACKTEST");
        assertThat(response.message()).isEqualTo("没有可用的回测结果");
    }

    @Test
    void okKeepsCodeNull() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.code()).isNull();
        assertThat(response.message()).isNull();
    }
}
