package com.fundpilot.backend.discipline.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
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
    @Autowired UserAdministrationApi users;
    @Autowired FundProductApi products;

    private static final String VALID_STRATEGY_BODY = """
            {"profitActivationPercent":0.20,"stopLossPullbackPercent":0.08,
             "profitHarvestPercent":0.50,"minimumHoldingPercent":0.40,
             "maxSingleSellPercent":0.20,"cooldownTradingDays":10,
             "presetFundCategory":"SECTOR","presetVersion":1,"customized":false}
            """;

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

    @Test
    void strategyCreateRejectsMissingRequiredCustomizedFlag() throws Exception {
        var fund = funds.create(new FundCreateRequest(
                "009997", "策略参数校验测试基金", FundCategory.SECTOR, FundSubType.INDEX, null));
        long portfolioFundId = portfolioFunds.findOwnedByLegacyFundId(testActorId(), fund.id())
                .orElseThrow().id();

        mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", portfolioFundId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profitActivationPercent":0.20,"stopLossPullbackPercent":0.08,
                                 "profitHarvestPercent":0.50,"minimumHoldingPercent":0.40,
                                 "maxSingleSellPercent":0.20,"cooldownTradingDays":10,
                                 "presetFundCategory":"SECTOR","presetVersion":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STRATEGY_PARAM_INVALID"));
    }

    @Test
    void strategyPortfolioFundCreateUpdateAndActivationUsePortfolioFundScope() throws Exception {
        var fund = funds.create(new FundCreateRequest(
                "009996", "策略创建更新测试基金", FundCategory.SECTOR, FundSubType.INDEX, null));
        long portfolioFundId = portfolioFunds.findOwnedByLegacyFundId(testActorId(), fund.id())
                .orElseThrow().id();

        String created = mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", portfolioFundId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFundId))
                .andExpect(jsonPath("$.data.status").value("PENDING_CALIBRATION"))
                .andExpect(jsonPath("$.data.presetFundCategory").value("SECTOR"))
                .andExpect(jsonPath("$.data.customized").value(false))
                .andExpect(jsonPath("$.data.takeProfitPhase").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long firstStrategyId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();

        mockMvc.perform(put("/api/discipline/strategies/{strategyId}", firstStrategyId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profitActivationPercent":0.25,"stopLossPullbackPercent":0.07,
                                 "profitHarvestPercent":0.60,"minimumHoldingPercent":0.35,
                                 "maxSingleSellPercent":0.30,"cooldownTradingDays":0,
                                 "presetFundCategory":"SECTOR","presetVersion":1,"customized":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFundId))
                .andExpect(jsonPath("$.data.status").value("PENDING_CALIBRATION"))
                .andExpect(jsonPath("$.data.profitActivationPercent").value(0.25))
                .andExpect(jsonPath("$.data.customized").value(true));

        mockMvc.perform(post("/api/discipline/strategies/{strategyId}/activate", firstStrategyId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.data.takeProfitPhase").value("ACCUMULATING"));

        String second = mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", portfolioFundId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long secondStrategyId = ((Number) com.jayway.jsonpath.JsonPath.read(second, "$.data.id")).longValue();

        mockMvc.perform(post("/api/discipline/strategies/{strategyId}/activate", secondStrategyId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EFFECTIVE"));
        mockMvc.perform(get("/api/discipline/strategies/portfolio-funds/{portfolioFundId}/active", portfolioFundId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(secondStrategyId))
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFundId));
    }

    @Test
    void strategyRejectsUnknownForeignAndVoidedPortfolioFunds() throws Exception {
        mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", Long.MAX_VALUE)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));

        var foreignOwner = users.create(new CurrentActorApi.Actor(testActorId(),
                CurrentActorApi.ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest("discipline-foreign-owner", "test-password",
                        UserAdministrationApi.Role.USER));
        var product = products.ensure(new FundProductApi.EnsureProduct(
                "009995", "他人策略隔离测试基金", null, null));
        var foreignFund = portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                null, foreignOwner.id(), product.id(), true, new BigDecimal("0.30")));

        mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", foreignFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));

        var ownFund = funds.create(new FundCreateRequest(
                "009994", "策略作废边界测试基金", FundCategory.SECTOR, FundSubType.INDEX, null));
        long ownPortfolioFundId = portfolioFunds.findOwnedByLegacyFundId(testActorId(), ownFund.id())
                .orElseThrow().id();
        String created = mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", ownPortfolioFundId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long strategyId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();

        portfolioFunds.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(
                testActorId(), ownPortfolioFundId, testActorId(), "策略作废边界测试",
                Instant.parse("2026-07-29T00:00:00Z")));

        mockMvc.perform(post("/api/discipline/strategies/portfolio-funds/{portfolioFundId}", ownPortfolioFundId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
        mockMvc.perform(put("/api/discipline/strategies/{strategyId}", strategyId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STRATEGY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    }
}
