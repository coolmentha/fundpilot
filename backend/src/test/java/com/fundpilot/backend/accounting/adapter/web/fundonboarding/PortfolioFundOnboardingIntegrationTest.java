package com.fundpilot.backend.accounting.adapter.web.fundonboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.FundPilotBackendApplication;
import tools.jackson.databind.ObjectMapper;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingCommandHandler;
import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingFailure;
import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@SpringBootTest(classes = FundPilotBackendApplication.class)
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class PortfolioFundOnboardingIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired FundProductApi products;
    @Autowired PublishedNavRepository navs;
    @Autowired SessionTokenGateway sessions;
    @Autowired UserAdministrationApi users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PortfolioFundOnboardingCommandHandler onboarding;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean MarketIndicatorRefreshCommandHandler marketRefresh;

    @Test
    void onboardingPersistsAtomicPortfolioLedgerAndOwnedViewsBeforeAsyncRefreshFailure() throws Exception {
        var product = product("success");
        navs.saveAll(List.of(PublishedNav.publish(null, product.id(), product.fundCode(),
                Instant.parse("2026-08-30T00:00:00Z"), new BigDecimal("3.60"),
                new BigDecimal("4.20"), Instant.parse("2026-08-30T08:00:00Z"))));
        doThrow(new RuntimeException("controlled refresh failure"))
                .when(marketRefresh).refreshOneForPortfolioFund(anyLong());

        String response = mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":1000,\"costPerShare\":3.50,"
                                + "\"openedAt\":\"2026-08-20T08:00:00Z\",\"groupNames\":[\"核心\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundProductId").value(product.id()))
                .andExpect(jsonPath("$.data.initialTransactionId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long portfolioFundId = objectMapper.readTree(response)
                .path("data").path("portfolioFundId").asLong();

        verify(marketRefresh, timeout(2000)).refreshOneForPortfolioFund(portfolioFundId);
        assertThat(count("portfolio_fund", "id", portfolioFundId)).isEqualTo(1);
        assertThat(count("fund_transaction", "portfolio_fund_id", portfolioFundId)).isEqualTo(1);
        assertThat(count("accounting_position", "portfolio_fund_id", portfolioFundId)).isEqualTo(1);
        assertThat(count("portfolio_fund_group_member", "portfolio_fund_id", portfolioFundId)).isEqualTo(1);

        mockMvc.perform(get("/api/portfolio-funds/{id}", portfolioFundId).cookie(ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(portfolioFundId))
                .andExpect(jsonPath("$.data.fundCode").value(product.fundCode()))
                .andExpect(jsonPath("$.data.validity").value("TRACKED"))
                .andExpect(jsonPath("$.data.groups[0].name").value("核心"));

        var other = users.create(new Actor(testActorId(), ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest("other-" + UUID.randomUUID(),
                        "integration-test-password", UserAdministrationApi.Role.USER));
        Cookie otherCookie = new Cookie(AuthenticationFilter.COOKIE_NAME,
                sessions.issue(other.id(), UserRole.USER, 0L));
        mockMvc.perform(get("/api/portfolio-funds").cookie(otherCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/portfolio-funds/{id}", portfolioFundId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_ALREADY_TRACKED"));
    }

    @Test
    void unavailableNavAndInvalidGroupRollBackPortfolioAndLedger() throws Exception {
        var noNav = product("no-nav");
        long portfoliosBefore = jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class);
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);
        long bridgesBefore = jdbc.queryForObject("SELECT count(*) FROM fund WHERE owner_id = ? AND product_id = ?",
                Long.class, testActorId(), noNav.id());

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + noNav.id() + ",\"initialHoldingShares\":10}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NAV_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund WHERE owner_id = ? AND product_id = ?",
                Long.class, testActorId(), noNav.id())).isEqualTo(bridgesBefore);

        var invalidGroup = product("invalid-group");
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + invalidGroup.id()
                                + ",\"groupNames\":[\"   \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_INVALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
    }

    @Test
    void onboardingHttpErrorMatrixAndNoInitialHoldingListView() throws Exception {
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":9223372036854775807}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        var invalidRatio = product("invalid-ratio");
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + invalidRatio.id()
                                + ",\"positionWarningRatio\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POSITION_WARNING_INVALID"));

        var invalidShares = product("invalid-shares");
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + invalidShares.id()
                                + ",\"initialHoldingShares\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INITIAL_HOLDING_SHARES_INVALID"));

        var invalidCost = product("invalid-cost");
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + invalidCost.id()
                                + ",\"initialHoldingShares\":10,\"costPerShare\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));

        var futureOpenedAt = product("future-opened-at");
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + futureOpenedAt.id()
                                + ",\"initialHoldingShares\":10,\"openedAt\":\"2999-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPENED_AT_IN_FUTURE"));

        var duplicateGroup = product("duplicate-group");
        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + duplicateGroup.id()
                                + ",\"groupNames\":[\"核心\",\" 核心 \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_DUPLICATE"));

        var noInitialHolding = products.ensure(new FundProductApi.EnsureProduct(
                "P" + Long.toUnsignedString(System.nanoTime(), 36), "沪深300 ETF", null,
                FundProductApi.InvestmentTarget.STOCK));
        String response = mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + noInitialHolding.id()
                                + ",\"groupNames\":[\"观察\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundProductId").value(noInitialHolding.id()))
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(response).path("data");
        assertThat(data.path("initialTransactionId").isNull()).isTrue();
        long portfolioFundId = data.path("portfolioFundId").asLong();

        String listResponse = mockMvc.perform(get("/api/portfolio-funds").cookie(ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        var listed = java.util.stream.StreamSupport.stream(
                        objectMapper.readTree(listResponse).path("data").spliterator(), false)
                .filter(item -> item.path("portfolioFundId").asLong() == portfolioFundId)
                .findFirst().orElseThrow();
        assertThat(listed.path("fundProductId").asLong()).isEqualTo(noInitialHolding.id());
        assertThat(listed.path("fundCode").asText()).isEqualTo(noInitialHolding.fundCode());
        assertThat(listed.path("fundName").asText()).isEqualTo("沪深300 ETF");
        assertThat(listed.path("productType").asText()).isEqualTo("ETF");
        assertThat(listed.path("investmentTarget").asText()).isEqualTo("STOCK");
        assertThat(listed.path("benchmarkIndexCode").asText()).isEqualTo("000300.SH");
        assertThat(listed.path("validity").asText()).isEqualTo("TRACKED");
        assertThat(listed.path("positionWarningEnabled").asBoolean()).isTrue();
        assertThat(listed.path("positionWarningRatio").decimalValue()).isEqualByComparingTo("0.30");
        assertThat(listed.path("groups").size()).isEqualTo(1);
        assertThat(listed.path("groups").get(0).path("name").asText()).isEqualTo("观察");
    }

    @Test
    void onboardingRejectsInitialSharesOutsideNumericPrecisionWithoutPersistence() throws Exception {
        var product = product("invalid-share-precision");
        long portfoliosBefore = jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class);
        long bridgesBefore = jdbc.queryForObject("SELECT count(*) FROM fund", Long.class);
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":100000000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INITIAL_HOLDING_SHARES_INVALID"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund", Long.class)).isEqualTo(bridgesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);
    }

    @Test
    void onboardingRejectsInitialSharesThatRoundToZeroBeforePersistence() throws Exception {
        var product = product("invalid-share-rounds-to-zero");
        long portfoliosBefore = jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class);
        long bridgesBefore = jdbc.queryForObject("SELECT count(*) FROM fund", Long.class);
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":0.001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INITIAL_HOLDING_SHARES_INVALID"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund", Long.class)).isEqualTo(bridgesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);
    }

    @Test
    void onboardingRejectsInitialTransactionAmountOutsideNumericPrecisionWithoutPersistence() throws Exception {
        var product = product("invalid-initial-amount-precision");
        navs.saveAll(List.of(PublishedNav.publish(null, product.id(), product.fundCode(),
                Instant.parse("2026-08-07T00:00:00Z"), new BigDecimal("100000"), new BigDecimal("100000"),
                Instant.parse("2026-08-07T08:00:00Z"))));
        long portfoliosBefore = jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class);
        long bridgesBefore = jdbc.queryForObject("SELECT count(*) FROM fund", Long.class);
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":99999999999.99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INITIAL_HOLDING_SHARES_INVALID"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund", Long.class)).isEqualTo(bridgesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);
    }

    @Test
    void onboardingRejectsInitialSharesThatRoundBeyondNumericPrecisionBeforePersistence() throws Exception {
        var product = product("invalid-share-rounding-overflow");
        long portfoliosBefore = jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class);
        long bridgesBefore = jdbc.queryForObject("SELECT count(*) FROM fund", Long.class);
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":99999999999.99999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INITIAL_HOLDING_SHARES_INVALID"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund", Long.class)).isEqualTo(bridgesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);
    }

    @Test
    void onboardingRejectsWarningRatioRoundedToZeroWithoutPersistence() throws Exception {
        var product = product("invalid-warning-precision");
        long portfoliosBefore = jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class);
        long bridgesBefore = jdbc.queryForObject("SELECT count(*) FROM fund", Long.class);
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"positionWarningRatio\":0.000000001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POSITION_WARNING_INVALID"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund", Long.class))
                .isEqualTo(portfoliosBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund", Long.class)).isEqualTo(bridgesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);
    }

    @Test
    void onboardingUsesLatestPositiveNavAndRejectsInvalidCostsWithoutPersistence() throws Exception {
        for (BigDecimal invalidNav : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1")}) {
            var product = product("latest-invalid-nav");
            insertRawNav(product.id(), product.fundCode(), Instant.parse("2026-08-01T00:00:00Z"),
                    new BigDecimal("3.25"));
            insertRawNav(product.id(), product.fundCode(), Instant.parse("2026-08-02T00:00:00Z"), invalidNav);

            assertThat(navs.findLatestByProductId(product.id())).hasValueSatisfying(nav ->
                    assertThat(nav.unitNav()).isEqualByComparingTo("3.25"));
            String response = mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                            .contentType("application/json")
                            .content("{\"fundProductId\":" + product.id()
                                    + ",\"initialHoldingShares\":10}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long portfolioFundId = objectMapper.readTree(response).path("data").path("portfolioFundId").asLong();
            assertThat(jdbc.queryForObject("SELECT nav FROM fund_transaction WHERE portfolio_fund_id = ?",
                    BigDecimal.class, portfolioFundId)).isEqualByComparingTo("3.25");
        }

        var noPositive = product("all-invalid-nav");
        insertRawNav(noPositive.id(), noPositive.fundCode(), Instant.parse("2026-08-03T00:00:00Z"), null);
        insertRawNav(noPositive.id(), noPositive.fundCode(), Instant.parse("2026-08-04T00:00:00Z"), BigDecimal.ZERO);
        insertRawNav(noPositive.id(), noPositive.fundCode(), Instant.parse("2026-08-05T00:00:00Z"),
                new BigDecimal("-1"));
        long transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class);
        long lotsBefore = jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class);
        long positionsBefore = jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class);

        mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + noPositive.id()
                                + ",\"initialHoldingShares\":10}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NAV_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund WHERE owner_id = ? AND fund_product_id = ?",
                Long.class, testActorId(), noPositive.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund WHERE owner_id = ? AND product_id = ?",
                Long.class, testActorId(), noPositive.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transaction", Long.class))
                .isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_lot", Long.class)).isEqualTo(lotsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_position", Long.class))
                .isEqualTo(positionsBefore);

        for (String cost : new String[]{"0.000000001", "100000000000.00000000"}) {
            var invalidCost = product("invalid-initial-cost");
            mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                            .contentType("application/json")
                            .content("{\"fundProductId\":" + invalidCost.id()
                                    + ",\"initialHoldingShares\":10,\"costPerShare\":" + cost + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio_fund WHERE owner_id = ? AND fund_product_id = ?",
                    Long.class, testActorId(), invalidCost.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fund WHERE owner_id = ? AND product_id = ?",
                    Long.class, testActorId(), invalidCost.id())).isZero();
        }
    }

    @Test
    void onboardingNormalizesInitialCostToPersistedScale() throws Exception {
        var product = product("normalized-cost");
        navs.saveAll(List.of(PublishedNav.publish(null, product.id(), product.fundCode(),
                Instant.parse("2026-08-06T00:00:00Z"), new BigDecimal("3.25"), new BigDecimal("3.25"),
                Instant.parse("2026-08-06T08:00:00Z"))));

        String response = mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":10,\"costPerShare\":1.234567891}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long portfolioFundId = objectMapper.readTree(response).path("data").path("portfolioFundId").asLong();

        assertThat(jdbc.queryForObject("SELECT cost_per_share FROM accounting_position WHERE portfolio_fund_id = ?",
                BigDecimal.class, portfolioFundId)).isEqualByComparingTo("1.23456789");
        assertThat(jdbc.queryForObject("SELECT acquire_cost_per_share FROM fund_lot WHERE portfolio_fund_id = ?",
                BigDecimal.class, portfolioFundId)).isEqualByComparingTo("1.23456789");
    }

    @Test
    void concurrentOnboardingUsesConflictAsStableAlreadyTrackedFailure() throws Exception {
        long ownerId = testActorId();
        var product = product("concurrent-onboarding");
        CyclicBarrier barrier = new CyclicBarrier(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<OnboardingOutcome> first = null;
        Future<OnboardingOutcome> second = null;
        try {
            first = executor.submit(() -> onboardInTransaction(ownerId, product.id(), barrier, transaction));
            second = executor.submit(() -> onboardInTransaction(ownerId, product.id(), barrier, transaction));
            List<OnboardingOutcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(value -> value.portfolioFundId() != null)).hasSize(1);
            assertThat(outcomes.stream().filter(value -> value.failureCode()
                    == PortfolioFundOnboardingFailure.Code.PORTFOLIO_FUND_ALREADY_TRACKED)).hasSize(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM portfolio_fund
                WHERE owner_id = ? AND fund_product_id = ? AND validity = 'TRACKED'
                """, Long.class, ownerId, product.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fund
                WHERE owner_id = ? AND product_id = ?
                """, Long.class, ownerId, product.id())).isEqualTo(1L);
    }

    private OnboardingOutcome onboardInTransaction(long ownerId, long productId,
                                                   CyclicBarrier barrier,
                                                   TransactionTemplate transaction) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            long portfolioFundId = transaction.execute(status -> onboarding.onboard(
                    null, ownerId, productId, true, new BigDecimal("0.30"),
                    null, null, null, List.of()).portfolioFundId());
            return new OnboardingOutcome(portfolioFundId, null);
        } catch (PortfolioFundOnboardingFailure failure) {
            return new OnboardingOutcome(null, failure.code());
        }
    }

    private FundProductApi.ProductReference product(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        return products.ensure(new FundProductApi.EnsureProduct(
                "P" + suffix, prefix + " ETF", null, FundProductApi.InvestmentTarget.STOCK));
    }

    private void insertRawNav(long productId, String fundCode, Instant navDate, BigDecimal nav) {
        jdbc.update("""
                INSERT INTO fund_nav_history
                    (fund_id, fund_product_id, fund_code, nav_date, nav, accumulated_nav,
                     first_seen_at, version, created_date, updated_date)
                VALUES (NULL, ?, ?, ?, ?, NULL, ?, 0, now(), now())
                """, new Object[]{productId, fundCode, Timestamp.from(navDate), nav, Timestamp.from(navDate)},
                new int[]{java.sql.Types.BIGINT, java.sql.Types.VARCHAR, java.sql.Types.TIMESTAMP,
                        java.sql.Types.NUMERIC, java.sql.Types.TIMESTAMP});
    }

    private Cookie ownerCookie() {
        return new Cookie(AuthenticationFilter.COOKIE_NAME,
                sessions.issue(testActorId(), UserRole.ADMIN, 0L));
    }

    private long count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, id);
    }

    private record OnboardingOutcome(Long portfolioFundId,
                                     PortfolioFundOnboardingFailure.Code failureCode) {
    }
}
