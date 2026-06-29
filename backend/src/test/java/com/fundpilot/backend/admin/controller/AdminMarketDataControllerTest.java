package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.market.service.TradingCalendarSyncService;
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
 * 用 {@code @WebMvcTest} 切片,Mock 掉 {@link MarketDataFetchService} 不触真实拉取。
 */
@WebMvcTest(controllers = AdminMarketDataController.class)
@Import({AdminMarketDataController.class, AdminMarketDataControllerTest.TestConfig.class})
class AdminMarketDataControllerTest {

    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MarketDataFetchService marketDataFetchService;

    // 285ca31 给 controller 加了 /sync-trading-calendar 端点,注入 TradingCalendarSyncService;
    // 285ca31 给 controller 加了 /sync-trading-calendar 端点,引入 TradingCalendarSyncService 依赖;
    // @WebMvcTest 切片不扫描 @Service,需显式 mock 该 bean 才能加载 ApplicationContext
    @MockitoBean
    TradingCalendarSyncService tradingCalendarSyncService;

    @Test
    void refresh_返回成功响应并调用_refreshAll() throws Exception {
        mockMvc.perform(post("/api/admin/market-data/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        verify(marketDataFetchService, times(1)).refreshAll();
    }
}
