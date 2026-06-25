package com.fundpilot.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * issue #4 验收:通过 {@link GlobalExceptionHandler @RestControllerAdvice} 把业务异常
 * 统一转成 {@link com.fundpilot.backend.common.ApiResponse#error} 响应。用 {@code @WebMvcTest}
 * 切片,只装配 MVC 层 + handler + 测试 Controller,不连数据库。
 *
 * <p><b>为什么用嵌套 {@link SpringBootConfiguration}</b>:{@code @WebMvcTest} 默认回退到主应用类
 * {@code FundPilotBackendApplication},而主类带 {@code @EnableJpaAuditing} 会强制创建
 * {@code jpaAuditingHandler},在无 JPA 的 web 切片里因"JPA metamodel must not be empty"启动失败。
 * 嵌套 {@code @SpringBootConfiguration} 让切片加载本测试配置而非主应用类,绕开 JPA auditing。
 */
@WebMvcTest(controllers = ErrorTestController.class)
@Import({GlobalExceptionHandler.class, ErrorTestController.class})
class GlobalExceptionHandlerTest {

    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void entityNotFoundReturns404WithEntityNotFoundCode() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void illegalStateTransitionReturns409WithIllegalStateTransitionCode() throws Exception {
        mockMvc.perform(get("/test/errors/illegal-transition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void businessExceptionReturns400WithItsCode() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.code").value("MIN_HOLD_DAYS_NOT_MET"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unexpectedExceptionReturns500WithInternalErrorCode() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }
}
