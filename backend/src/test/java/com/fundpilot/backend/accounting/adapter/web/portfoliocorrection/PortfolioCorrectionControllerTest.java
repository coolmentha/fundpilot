package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionCommandHandler;
import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioCorrectionController.class)
@Import({PortfolioCorrectionController.class, PortfolioCorrectionExceptionHandler.class,
        PortfolioCorrectionControllerTest.TestConfig.class})
class PortfolioCorrectionControllerTest {
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortfolioCorrectionCommandHandler commands;

    @Test
    void correctsCostBasisByPortfolioFundId() throws Exception {
        when(commands.correctCostPerShare(7L, 41L, new BigDecimal("3.40")))
                .thenReturn(new PortfolioCorrectionCommandHandler.CostCorrectionResult(
                        41L, new BigDecimal("3.40000000")));

        mockMvc.perform(put("/api/portfolio-funds/41/cost-basis")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"costPerShare\":3.40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.portfolioFundId").value(41))
                .andExpect(jsonPath("$.data.costPerShare").value(3.4));

        verify(commands).correctCostPerShare(7L, 41L, new BigDecimal("3.40"));
    }

    @Test
    void mapsCostValidationToBadRequest() throws Exception {
        when(commands.correctCostPerShare(7L, 41L, null))
                .thenThrow(new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID,
                        "成本单价必须大于 0"));

        mockMvc.perform(put("/api/portfolio-funds/41/cost-basis")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));
    }

    @Test
    void mapsMissingPortfolioFundToNotFound() throws Exception {
        when(commands.correctCostPerShare(7L, 41L, new BigDecimal("3.40")))
                .thenThrow(new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: 41"));

        mockMvc.perform(put("/api/portfolio-funds/41/cost-basis")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"costPerShare\":3.40}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));
    }

    @Test
    void mapsClosedPositionToConflict() throws Exception {
        when(commands.correctCostPerShare(7L, 41L, new BigDecimal("3.40")))
                .thenThrow(new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_OPEN,
                        "只有当前持仓可以修改成本单价"));

        mockMvc.perform(put("/api/portfolio-funds/41/cost-basis")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"costPerShare\":3.40}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_OPEN"));
    }
}
