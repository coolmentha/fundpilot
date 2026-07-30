package com.fundpilot.backend.marketdata.adapter.web.tradingcalendar;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.marketdata.application.command.tradingcalendar.TradingCalendarCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TradingCalendarAdminController.class)
@Import({TradingCalendarAdminController.class, TradingCalendarAdminControllerTest.TestConfig.class})
class TradingCalendarAdminControllerTest {
    @SpringBootConfiguration static class TestConfig {}
    @Autowired MockMvc mockMvc;
    @MockitoBean TradingCalendarCommandHandler commands;

    @Test
    void delegatesFullSynchronization() throws Exception {
        when(commands.synchronize(false)).thenReturn(3);

        mockMvc.perform(post("/api/admin/market-data/sync-trading-calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(3));

        verify(commands).synchronize(false);
    }
}
