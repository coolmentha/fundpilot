package com.fundpilot.backend.investmentplan.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class InvestmentPlanWebIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired FundService funds;

    @Test
    void exposesPlanAndBudgetContracts() throws Exception {
        var fund = funds.create(new FundCreateRequest(
                "009997", "定投接口测试基金", FundCategory.SECTOR, FundSubType.INDEX, null));

        mockMvc.perform(post("/api/investment-plans/funds/{fundId}", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"amount":100.00,"frequency":"WEEKLY",
                                 "dayOfWeek":3,"dayOfMonth":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").isNumber())
                .andExpect(jsonPath("$.data.status").value("EFFECTIVE"));

        mockMvc.perform(put("/api/investment-plan-budget")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyBudget\":3000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyBudget").value(3000.00));
        mockMvc.perform(get("/api/investment-plan-budget")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyBudget").value(3000.00));
    }
}
