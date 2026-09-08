package com.fundpilot.backend.investmentplan.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
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
    @Autowired FundProductApi products;
    @Autowired PortfolioFundApi portfolioFunds;
    @Autowired UserAdministrationApi users;

    private static final String PLAN_BODY = """
            {"enabled":true,"amount":100.00,"frequency":"WEEKLY",
             "dayOfWeek":3,"dayOfMonth":null}
            """;

    @Test
    void planRoutesUsePortfolioFundIdsAndRejectLegacyOrForeignIdentifiers() throws Exception {
        var ownFund = track(testActorId(), "own");
        var foreignOwner = users.create(new CurrentActorApi.Actor(testActorId(),
                        CurrentActorApi.ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest("plan-foreign-" + UUID.randomUUID(),
                        "integration-test-password", UserAdministrationApi.Role.USER));
        var foreignFund = track(foreignOwner.id(), "foreign");

        mockMvc.perform(get("/api/investment-plans/funds/{legacyFundId}", ownFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/investment-plans/funds/{legacyFundId}/active", ownFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/investment-plans/funds/{legacyFundId}", ownFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN_BODY))
                .andExpect(status().isNotFound());

        String created = mockMvc.perform(post("/api/investment-plans/portfolio-funds/{portfolioFundId}", ownFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(ownFund.id()))
                .andExpect(jsonPath("$.data.status").value("EFFECTIVE"))
                .andReturn().getResponse().getContentAsString();
        long planId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();

        mockMvc.perform(get("/api/investment-plans/portfolio-funds/{portfolioFundId}", ownFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(planId));
        mockMvc.perform(put("/api/investment-plans/{planId}", planId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"amount":150.00,"frequency":"MONTHLY",
                                 "dayOfWeek":null,"dayOfMonth":15}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(ownFund.id()))
                .andExpect(jsonPath("$.data.amount").value(150.00));
        mockMvc.perform(post("/api/investment-plans/{planId}/pause", planId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        mockMvc.perform(post("/api/investment-plans/{planId}/resume", planId)
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post("/api/investment-plans/portfolio-funds/{portfolioFundId}", foreignFund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void exposesBudgetContracts() throws Exception {
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
        mockMvc.perform(get("/api/investment-plan-budget/summary")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyBudget").value(3000.00))
                .andExpect(jsonPath("$.data.investedAmount").value(0))
                .andExpect(jsonPath("$.data.confirmedInvestedAmount").value(0))
                .andExpect(jsonPath("$.data.pendingInvestedAmount").value(0))
                .andExpect(jsonPath("$.data.futurePlans").isEmpty());
    }

    private PortfolioFundApi.PortfolioFund track(long ownerId, String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        var product = products.ensure(new FundProductApi.EnsureProduct(
                "PLAN" + suffix, "定投入口" + prefix, null, FundProductApi.InvestmentTarget.STOCK));
        return portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                null, ownerId, product.id(), true, new BigDecimal("0.30")));
    }
}
