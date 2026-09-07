package com.fundpilot.backend.marketdata.adapter.web.indicatorrefresh;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(classes = FundPilotBackendApplication.class)
class PortfolioMarketEntryIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired FundProductApi products;
    @Autowired PortfolioFundApi portfolios;
    @Autowired SessionTokenGateway sessions;
    @Autowired UserAdministrationApi users;
    @MockitoBean PublishedNavSourceGateway source;

    @Test
    void portfolioWithoutLegacyIdCanRefreshPublishedNavAndReadMarketData() throws Exception {
        long id = create(testActorId());
        when(source.fetchHistory(anyString())).thenReturn(List.of(new PublishedNavSourceGateway.NavSnapshot(
                Instant.parse("2026-09-04T00:00:00Z"), new BigDecimal("1.20"), new BigDecimal("2.40"))));
        mvc.perform(post("/api/portfolio-funds/{id}/market-data/refresh", id).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.portfolioFundId").value(id));
        mvc.perform(get("/api/portfolio-funds/{id}/market-indicators/today", id).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentNav").value(2.40));
        mvc.perform(get("/api/portfolio-funds/{id}/kline", id).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.chartType").value("nav"));
        mvc.perform(get("/api/portfolio-funds/{id}/intraday", id).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidAndVoidedPortfolioAreRejectedBeforeExternalRefresh() throws Exception {
        long id = create(testActorId());
        portfolios.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(testActorId(), id,
                testActorId(), "测试作废", Instant.now()));
        assertRejected(id);
        assertRejected(Long.MAX_VALUE);
        verifyNoInteractions(source);
    }

    @Test
    void anotherOwnersPortfolioIsRejected() throws Exception {
        long id = create(testActorId());
        var other = users.create(new Actor(testActorId(), ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest("market-other-" + System.nanoTime(),
                        "integration-test-password", UserAdministrationApi.Role.USER));
        var foreignCookie = new Cookie(AuthenticationFilter.COOKIE_NAME,
                sessions.issue(other.id(), UserRole.USER, 0L));
        for (String suffix : List.of("intraday", "kline", "market-indicators/today")) {
            mvc.perform(get("/api/portfolio-funds/{id}/" + suffix, id).cookie(foreignCookie))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        mvc.perform(post("/api/portfolio-funds/{id}/market-data/refresh", id).cookie(foreignCookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(source);
    }

    @Test
    void sourceFailureIsNotReportedAsSuccess() throws Exception {
        long id = create(testActorId());
        when(source.fetchHistory(anyString())).thenThrow(new IllegalStateException("private upstream detail"));
        mvc.perform(post("/api/portfolio-funds/{id}/market-data/refresh", id).cookie(cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_ALL_SOURCES_FAILED"))
                .andExpect(jsonPath("$.message").value("行情刷新失败，请稍后重试"));
    }

    private void assertRejected(long id) throws Exception {
        for (String suffix : List.of("intraday", "kline", "market-indicators/today")) {
            mvc.perform(get("/api/portfolio-funds/{id}/" + suffix, id).cookie(cookie()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        mvc.perform(post("/api/portfolio-funds/{id}/market-data/refresh", id).cookie(cookie()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    private long create(long ownerId) {
        var product = products.ensure(new FundProductApi.EnsureProduct("MKT" + System.nanoTime(),
                "行情测试基金", null, FundProductApi.InvestmentTarget.STOCK));
        return portfolios.track(new PortfolioFundApi.TrackPortfolioFund(null, ownerId, product.id(),
                true, new BigDecimal("0.30"))).id();
    }

    private Cookie cookie() {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, sessions.issue(testActorId(), UserRole.ADMIN, 0L));
    }
}
