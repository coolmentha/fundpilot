package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.portfolio.application.query.fundtracking.PortfolioFundViewQueryHandler;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioFundController.class)
@Import({PortfolioFundController.class, PortfolioFundQueryExceptionHandler.class,
        PortfolioFundControllerTest.TestConfig.class})
class PortfolioFundControllerTest {
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortfolioFundViewQueryHandler queries;

    @Test
    void list_returnsCurrentOwnersPortfolioFundView() throws Exception {
        when(queries.findByOwner(7L)).thenReturn(List.of(view()));

        mockMvc.perform(get("/api/portfolio-funds")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].portfolioFundId").value(41))
                .andExpect(jsonPath("$.data[0].fundProductId").value(9))
                .andExpect(jsonPath("$.data[0].fundCode").value("510300"))
                .andExpect(jsonPath("$.data[0].productType").value("ETF"))
                .andExpect(jsonPath("$.data[0].groups[0].id").value(3))
                .andExpect(jsonPath("$.data[0].groups[0].name").value("核心"));
    }

    @Test
    void get_hidesAnotherOwnersPortfolioFundAsNotFound() throws Exception {
        when(queries.findOwned(7L, 41L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portfolio-funds/41")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));
    }

    private static PortfolioFundViewQueryHandler.ViewResult view() {
        return new PortfolioFundViewQueryHandler.ViewResult(41L, 9L, "510300", "沪深300ETF", "ETF",
                "STOCK", "000300.SH", "TRACKED", true, new BigDecimal("0.30"),
                List.of(new PortfolioFundViewQueryHandler.GroupResult(3L, "核心")));
    }
}
