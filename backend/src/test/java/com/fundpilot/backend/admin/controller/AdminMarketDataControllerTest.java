package com.fundpilot.backend.marketdata.adapter.web.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * issue #7 循环 G:{@code POST /api/admin/market-data/refresh} 手动触发当日全量刷新。
 * 用 {@code @WebMvcTest} 切片，Mock 掉刷新 Handler 不触真实拉取。
 */
@WebMvcTest(controllers = MarketIndicatorRefreshAdminController.class)
@Import({MarketIndicatorRefreshAdminController.class, AdminMarketDataControllerTest.TestConfig.class})
class AdminMarketDataControllerTest {

    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MarketIndicatorRefreshCommandHandler commands;

    @Test
    void refresh_返回成功响应并调用_refreshAll() throws Exception {
        mockMvc.perform(post("/api/admin/market-data/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        verify(commands, times(1)).refreshAll();
    }

}
