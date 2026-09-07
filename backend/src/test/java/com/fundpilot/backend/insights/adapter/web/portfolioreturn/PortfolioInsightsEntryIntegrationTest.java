package com.fundpilot.backend.insights.adapter.web.portfolioreturn;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.CreateUserRequest;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.Role;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.UserResult;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PortfolioInsightsEntryIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserAdministrationApi users;
    @Autowired FundProductApi products;
    @Autowired PortfolioFundApi portfolioFunds;
    @Autowired SessionTokenGateway sessions;

    @Test
    void returnsOwnedPortfolioFundByPortfolioIdAndHidesItFromAnotherOwnerWhenMarketDataIsMissing()
            throws Exception {
        Actor admin = new Actor(testActorId(), ActorRole.ADMIN, true);
        UserResult owner = users.create(admin, new CreateUserRequest(unique("insights-owner"),
                "integration-test-password", Role.USER));
        UserResult other = users.create(admin, new CreateUserRequest(unique("insights-other"),
                "integration-test-password", Role.USER));
        String code = UUID.randomUUID().toString().substring(0, 8);
        FundProductApi.ProductReference product = products.ensure(new FundProductApi.EnsureProduct(code,
                "缺行情基金", null, null));
        PortfolioFundApi.PortfolioFund portfolioFund = portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                null, owner.id(), product.id(), true, new BigDecimal("0.30")));

        mockMvc.perform(get("/api/insights/portfolio/funds/{portfolioFundId}", portfolioFund.id())
                .cookie(cookie(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFund.id()))
                .andExpect(jsonPath("$.data.estimateStatus").value("NOT_ATTEMPTED"))
                .andExpect(jsonPath("$.data.valuationNav").doesNotExist());

        mockMvc.perform(get("/api/insights/portfolio/funds/{portfolioFundId}", portfolioFund.id())
                        .cookie(cookie(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/insights/portfolio/funds/{portfolioFundId}", Long.MAX_VALUE)
                        .cookie(cookie(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static String unique(String prefix) {
        return prefix + '-' + UUID.randomUUID();
    }

    private Cookie cookie(UserResult user) {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, sessions.issue(user.id(), UserRole.USER, 0L));
    }
}
