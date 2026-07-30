package com.fundpilot.backend.discipline.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
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
class DisciplineWebIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired FundService funds;
    @Autowired AdviceRepository advice;
    @Autowired PortfolioFundApi portfolioFunds;

    @Test
    void exposesDisciplineStrategyAndAdviceResponseContracts() throws Exception {
        var fund = funds.create(new FundCreateRequest(
                "009998", "纪律接口测试基金", FundCategory.SECTOR, FundSubType.INDEX, null));

        mockMvc.perform(get("/api/discipline/strategies/funds/{fundId}/recommendation", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundCategory").value("SECTOR"))
                .andExpect(jsonPath("$.data.profitActivationPercent").value(0.20));

        long strategyId = ((Number) com.jayway.jsonpath.JsonPath.read(mockMvc.perform(
                        post("/api/discipline/strategies/funds/{fundId}", fund.id())
                                .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"profitActivationPercent":0.20,"stopLossPullbackPercent":0.08,
                                         "profitHarvestPercent":0.50,"minimumHoldingPercent":0.40,
                                         "maxSingleSellPercent":0.20,"cooldownTradingDays":10,
                                         "presetFundCategory":"SECTOR","presetVersion":1,"customized":false}
                                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();

        long portfolioFundId = portfolioFunds.findOwnedByLegacyFundId(testActorId(), fund.id())
                .orElseThrow().id();
        advice.replaceGenerated(portfolioFundId, testActorId(), strategyId,
                Instant.parse("2026-07-29T00:00:00Z"), AdviceAction.BUILD, 1, BigDecimal.ONE,
                new BigDecimal("100.00"), "AMOUNT", "DRAWDOWN_TIER", null, null);

        int adviceId = ((Number) com.jayway.jsonpath.JsonPath.read(mockMvc.perform(
                        get("/api/discipline/advice/pending")
                                .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("BUILD"))
                .andExpect(jsonPath("$.data[0].responseStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "$.data[0].id")).intValue();

        mockMvc.perform(post("/api/discipline/advice/{adviceId}/ignore", adviceId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discipline/advice/pending")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
