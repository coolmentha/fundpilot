package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(classes = FundPilotBackendApplication.class)
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class PortfolioFundConfigurationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired FundProductApi products;
    @Autowired PortfolioFundApi portfolioFunds;
    @Autowired SessionTokenGateway sessions;
    @Autowired UserAdministrationApi users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void warningEndpointEnforcesRatioOwnershipAndVoidedBoundary() throws Exception {
        long portfolioFundId = createPortfolioFund();

        mockMvc.perform(put("/api/portfolio-funds/{id}/position-warning", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"ratio\":0.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFundId))
                .andExpect(jsonPath("$.data.positionWarningEnabled").value(false))
                .andExpect(jsonPath("$.data.positionWarningRatio").value(0.25));
        assertThat(jdbc.queryForObject(
                "SELECT position_warning_enabled FROM portfolio_fund WHERE id = ?", Boolean.class,
                portfolioFundId)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT position_warning_ratio FROM portfolio_fund WHERE id = ?", BigDecimal.class,
                portfolioFundId)).isEqualByComparingTo("0.25");

        mockMvc.perform(put("/api/portfolio-funds/{id}/position-warning", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POSITION_WARNING_INVALID"));

        mockMvc.perform(put("/api/portfolio-funds/{id}/position-warning", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":1.000000004}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POSITION_WARNING_INVALID"));

        var other = users.create(new Actor(testActorId(), ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest("config-other-" + UUID.randomUUID(),
                        "integration-test-password", UserAdministrationApi.Role.USER));
        mockMvc.perform(put("/api/portfolio-funds/{id}/position-warning", portfolioFundId)
                        .cookie(cookie(other.id(), UserRole.USER))
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":0.50}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));

        portfolioFunds.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(
                testActorId(), portfolioFundId, testActorId(), "测试作废",
                Instant.parse("2026-09-04T00:00:00Z")));
        mockMvc.perform(put("/api/portfolio-funds/{id}/position-warning", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":0.50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_VOIDED"));
    }

    @Test
    void groupsEndpointReplacesClearsAndValidatesNamesAndOwnership() throws Exception {
        long portfolioFundId = createPortfolioFund();

        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[\" 核心 \",\"卫星\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFundId))
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].name").value("核心"))
                .andExpect(jsonPath("$.data.groups[1].name").value("卫星"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM portfolio_fund_group_member WHERE portfolio_fund_id = ?", Integer.class,
                portfolioFundId)).isEqualTo(2);

        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(0));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM portfolio_fund_group_member WHERE portfolio_fund_id = ?", Integer.class,
                portfolioFundId)).isZero();

        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"核心\",\" 核心 \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_DUPLICATE"));
        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"   \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_INVALID"));

        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"\\u0007核心\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_INVALID"));
        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"核心\\u0000\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_INVALID"));

        portfolioFunds.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(
                testActorId(), portfolioFundId, testActorId(), "测试作废",
                Instant.parse("2026-09-04T00:00:00Z")));
        mockMvc.perform(put("/api/portfolio-funds/{id}/groups", portfolioFundId)
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"核心\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));
    }

    private long createPortfolioFund() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        var product = products.ensure(new FundProductApi.EnsureProduct(
                "CFG" + suffix, "配置测试基金", null, FundProductApi.InvestmentTarget.STOCK));
        return portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                null, testActorId(), product.id(), true, new BigDecimal("0.30"))).id();
    }

    private Cookie cookie(long ownerId, UserRole role) {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, sessions.issue(ownerId, role, 0L));
    }
}
