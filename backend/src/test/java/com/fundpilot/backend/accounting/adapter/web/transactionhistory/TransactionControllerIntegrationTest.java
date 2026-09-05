package com.fundpilot.backend.accounting.adapter.web.transactionhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(classes = FundPilotBackendApplication.class)
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class TransactionControllerIntegrationTest extends AbstractIntegrationTest {
    private static final Instant TRADE_DAY = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant TRADE_DATE = Instant.parse("2026-08-20T08:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired FundProductApi products;
    @Autowired PortfolioFundApi portfolioFunds;
    @Autowired PortfolioFundOnboardingApi onboarding;
    @Autowired PublishedNavRepository navs;
    @Autowired TransactionRepository transactions;
    @Autowired PositionApi positions;
    @Autowired SessionTokenGateway sessions;
    @Autowired UserAdministrationApi users;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transactionRoutesUsePortfolioFundIdsAndRejectLegacyOrForeignIdentifiers() throws Exception {
        long ownerId = testActorId();
        var sourceProduct = product("source");
        var targetProduct = product("target");
        var source = track(ownerId, sourceProduct.id());
        var target = track(ownerId, targetProduct.id());
        var other = users.create(new Actor(ownerId, ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest(
                        "transaction-other-" + UUID.randomUUID(), "integration-test-password",
                        UserAdministrationApi.Role.USER));
        var foreign = track(other.id(), sourceProduct.id());
        Cookie ownerCookie = cookie(ownerId, UserRole.ADMIN);

        mockMvc.perform(get("/api/portfolio-funds/{id}/transactions", source.id()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/funds/{id}/transactions", source.id()).cookie(ownerCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/funds/{id}/transactions", source.id()).cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":100}"))
                .andExpect(status().isNotFound());

        String response = mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", source.id())
                        .cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":100,"
                                + "\"tradeDate\":\"2026-08-20T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.portfolioFundId").value(source.id()))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("targetFundId");

        mockMvc.perform(get("/api/portfolio-funds/{id}/transactions", source.id()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].portfolioFundId").value(source.id()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", foreign.id()).cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));

        mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", source.id()).cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":100,"
                                + "\"targetPortfolioFundId\":" + target.id() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_INPUT_REQUIRED"));

        mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", source.id()).cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"TRANSFER_OUT\",\"shares\":10,"
                                + "\"targetPortfolioFundId\":" + foreign.id() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));

        mockMvc.perform(get("/api/portfolio-funds/{id}/transactions", foreign.id()).cookie(ownerCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));

        assertThat(target.id()).isPositive();
        assertThat(source.id()).isNotEqualTo(foreign.id());
    }

    @Test
    void pendingConfirmationAndCancellationPreserveFactSemantics() throws Exception {
        long ownerId = testActorId();
        var product = product("lifecycle");
        var portfolioFund = track(ownerId, product.id());
        publishNav(product, "2.00");
        Cookie ownerCookie = cookie(ownerId, UserRole.ADMIN);

        String created = mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", portfolioFund.id())
                        .cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":100,"
                                + "\"tradeDate\":\"" + TRADE_DATE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long pendingId = transactionId(created);

        assertThat(transactions.findByPortfolioFundAndStatus(portfolioFund.id(), TransactionStatus.CONFIRMED))
                .isEmpty();
        assertThat(positions.findOwned(ownerId, portfolioFund.id())).isEmpty();

        mockMvc.perform(post("/api/transactions/{id}/confirm", pendingId).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(pendingId))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));

        LedgerTransaction confirmed = transactions.findById(pendingId).orElseThrow();
        assertThat(confirmed.status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(confirmed.shares()).isEqualByComparingTo("50.00");
        assertThat(positions.findOwned(ownerId, portfolioFund.id())).get()
                .satisfies(position -> {
                    assertThat(position.status()).isEqualTo(PositionApi.Status.OPEN);
                    assertThat(position.confirmedShares()).isEqualByComparingTo("50.00");
                });

        String cancellable = mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", portfolioFund.id())
                        .cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":40,"
                                + "\"tradeDate\":\"" + TRADE_DATE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long cancelledId = transactionId(cancellable);

        mockMvc.perform(post("/api/transactions/{id}/cancel", cancelledId).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(cancelledId))
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"));

        assertThat(transactions.findById(cancelledId)).get()
                .extracting(LedgerTransaction::status).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(transactions.findByPortfolioFundAndStatus(portfolioFund.id(), TransactionStatus.CONFIRMED))
                .extracting(LedgerTransaction::id).containsExactly(pendingId);
        assertThat(positions.findOwned(ownerId, portfolioFund.id())).get()
                .satisfies(position -> assertThat(position.confirmedShares()).isEqualByComparingTo("50.00"));
    }

    @Test
    void conversionBindsBothLegsToPortfolioFundsAndConfirmsAtomically() throws Exception {
        long ownerId = testActorId();
        var sourceProduct = product("conversion-source");
        var targetProduct = product("conversion-target");
        publishNav(sourceProduct, "2.00");
        publishNav(targetProduct, "1.00");
        var source = onboarding.onboard(new PortfolioFundOnboardingApi.OnboardPortfolioFund(
                null, ownerId, sourceProduct.id(), true, new BigDecimal("0.30"),
                new BigDecimal("10"), new BigDecimal("2.00"), TRADE_DATE));
        var target = track(ownerId, targetProduct.id());
        Cookie ownerCookie = cookie(ownerId, UserRole.ADMIN);

        String created = mockMvc.perform(post("/api/portfolio-funds/{id}/transactions", source.portfolioFundId())
                        .cookie(ownerCookie)
                        .contentType("application/json")
                        .content("{\"source\":\"TRANSFER_OUT\",\"shares\":4,"
                                + "\"targetPortfolioFundId\":" + target.id() + ","
                                + "\"tradeDate\":\"" + TRADE_DATE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(source.portfolioFundId()))
                .andExpect(jsonPath("$.data.source").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        var createdData = objectMapper.readTree(created).path("data");
        long outId = createdData.path("id").asLong();
        long inId = createdData.path("relatedTransactionId").asLong();

        assertThat(inId).isPositive();
        assertThat(transactions.findById(inId)).get().satisfies(inLeg -> {
            assertThat(inLeg.portfolioFundId()).isEqualTo(target.id());
            assertThat(inLeg.source()).isEqualTo(TransactionSource.TRANSFER_IN);
            assertThat(inLeg.status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(inLeg.amount()).isNull();
            assertThat(inLeg.shares()).isNull();
            assertThat(inLeg.relatedTransactionId()).isEqualTo(outId);
        });
        assertThat(positions.findOwned(ownerId, source.portfolioFundId())).get()
                .satisfies(position -> assertThat(position.confirmedShares()).isEqualByComparingTo("10.00"));
        assertThat(positions.findOwned(ownerId, target.id())).isEmpty();

        mockMvc.perform(post("/api/transactions/{id}/confirm", outId).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));

        assertThat(transactions.findById(outId)).get().satisfies(outLeg -> {
            assertThat(outLeg.portfolioFundId()).isEqualTo(source.portfolioFundId());
            assertThat(outLeg.source()).isEqualTo(TransactionSource.TRANSFER_OUT);
            assertThat(outLeg.status()).isEqualTo(TransactionStatus.CONFIRMED);
            assertThat(outLeg.shares()).isEqualByComparingTo("4.00");
            assertThat(outLeg.amount()).isEqualByComparingTo("8.00");
            assertThat(outLeg.relatedTransactionId()).isEqualTo(inId);
        });
        assertThat(transactions.findById(inId)).get().satisfies(inLeg -> {
            assertThat(inLeg.portfolioFundId()).isEqualTo(target.id());
            assertThat(inLeg.source()).isEqualTo(TransactionSource.TRANSFER_IN);
            assertThat(inLeg.status()).isEqualTo(TransactionStatus.CONFIRMED);
            assertThat(inLeg.amount()).isEqualByComparingTo("8.00");
            assertThat(inLeg.shares()).isEqualByComparingTo("8.00");
            assertThat(inLeg.relatedTransactionId()).isEqualTo(outId);
        });
        assertThat(positions.findOwned(ownerId, source.portfolioFundId())).get()
                .satisfies(position -> assertThat(position.confirmedShares()).isEqualByComparingTo("6.00"));
        assertThat(positions.findOwned(ownerId, target.id())).get()
                .satisfies(position -> assertThat(position.confirmedShares()).isEqualByComparingTo("8.00"));
    }

    private FundProductApi.ProductReference product(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        return products.ensure(new FundProductApi.EnsureProduct(
                "T" + suffix, "交易入口" + prefix, null, FundProductApi.InvestmentTarget.STOCK));
    }

    private PortfolioFundApi.PortfolioFund track(long ownerId, long productId) {
        return portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                null, ownerId, productId, true, new BigDecimal("0.30")));
    }

    private Cookie cookie(long ownerId, UserRole role) {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, sessions.issue(ownerId, role, 0L));
    }

    private void publishNav(FundProductApi.ProductReference product, String unitNav) {
        BigDecimal nav = new BigDecimal(unitNav);
        navs.saveAll(List.of(PublishedNav.publish(null, product.id(), product.fundCode(), TRADE_DAY,
                nav, nav, TRADE_DAY)));
    }

    private long transactionId(String response) throws Exception {
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
}
