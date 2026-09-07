package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.application.query.returnfacts.AccountingReturnQueryHandler;
import com.fundpilot.backend.discipline.adapter.api.classification.DisciplineClassificationApi;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@AutoConfigureMockMvc
@SpringBootTest(classes = FundPilotBackendApplication.class)
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class PortfolioCorrectionIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired PortfolioFundApi portfolioFundApi;
    @Autowired FundProductApi products;
    @Autowired DisciplineClassificationApi classifications;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PositionRepository positions;
    @Autowired TransactionRepository ledgerTransactions;
    @Autowired LotRepository lots;
    @Autowired AccountingReturnQueryHandler accountingReturns;
    @Autowired SessionTokenGateway sessions;
    @Autowired UserAdministrationApi users;
    @Autowired EntityManager entityManager;

    @Test
    void correctsCurrentCostThroughFundUpdateAndMapsInvalidInput() throws Exception {
        var fund = track("009998", "当前成本测试基金");
        var position = Position.empty(fund.id(), testActorId());
        position.reconcile(true, new java.math.BigDecimal("100"), java.time.Instant.parse("2026-08-01T00:00:00Z"));
        position.applyExistingPosition(new java.math.BigDecimal("1.10"),
                java.time.Instant.parse("2026-08-01T00:00:00Z"));
        positions.save(position);
        LedgerTransaction initialTransaction = ledgerTransactions.save(LedgerTransaction.recordExistingPosition(
                fund.id(), testActorId(), new java.math.BigDecimal("100"),
                new java.math.BigDecimal("1.10"), java.time.Instant.parse("2026-08-01T00:00:00Z"),
                java.time.Instant.parse("2026-08-01T00:00:00Z")));
        Lot initialLot = lots.save(Lot.open(fund.id(), initialTransaction.id(),
                java.time.Instant.parse("2026-08-01T00:00:00Z"), new java.math.BigDecimal("100"),
                new java.math.BigDecimal("1.10")));
        LedgerTransaction sale = LedgerTransaction.placePending(fund.id(), testActorId(),
                com.fundpilot.backend.accounting.domain.transaction.TransactionSource.DECREASE, null,
                new java.math.BigDecimal("20"), java.time.Instant.parse("2026-08-10T00:00:00Z"), null, null);
        sale.confirm(new LedgerTransaction.Settlement(new java.math.BigDecimal("1.50"),
                new java.math.BigDecimal("30.00"), new java.math.BigDecimal("20"),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO),
                java.time.Instant.parse("2026-08-10T00:00:00Z"));
        sale = ledgerTransactions.save(sale);
        initialLot.consume(new java.math.BigDecimal("20"));
        lots.save(initialLot);
        lots.saveRedemptions(List.of(com.fundpilot.backend.accounting.domain.lot.LotRedemption.record(
                initialLot.id(), sale.id(), new java.math.BigDecimal("20"), 9, java.math.BigDecimal.ZERO)));
        var beforeReturns = accountingReturns.findByOwner(testActorId()).stream()
                .filter(value -> value.portfolioFundId() == fund.id()).findFirst().orElseThrow();
        assertThat(beforeReturns.investedAmount()).isEqualByComparingTo("110.00");
        assertThat(beforeReturns.redeemedAmount()).isEqualByComparingTo("30.00");
        assertThat(beforeReturns.realizedPnl()).isEqualByComparingTo("8.00");

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":1.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPerShare").value(1.25));

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":0.000000004}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));

        var confirmed = ledgerTransactions.findByPortfolioFundAndStatus(
                fund.id(), com.fundpilot.backend.accounting.domain.transaction.TransactionStatus.CONFIRMED);
        assertThat(confirmed).hasSize(3);
        LedgerTransaction storedInitial = confirmed.stream()
                .filter(transaction -> transaction.id().equals(initialTransaction.id()))
                .findFirst().orElseThrow();
        LedgerTransaction reset = confirmed.stream()
                .filter(transaction -> transaction.source()
                        == com.fundpilot.backend.accounting.domain.transaction.TransactionSource.COST_BASIS_RESET)
                .findFirst().orElseThrow();
        assertThat(storedInitial.amount()).isEqualByComparingTo("110.00");
        assertThat(storedInitial.shares()).isEqualByComparingTo("100.00");
        assertThat(storedInitial.nav()).isEqualByComparingTo("1.10");
        assertThat(reset.status()).isEqualTo(
                com.fundpilot.backend.accounting.domain.transaction.TransactionStatus.CONFIRMED);
        assertThat(reset.amount()).isEqualByComparingTo("100.00000000");
        assertThat(reset.shares()).isEqualByComparingTo("80.00");
        assertThat(reset.signedShares()).isZero();
        assertThat(reset.nav()).isNull();
        assertThat(reset.fee()).isNull();
        assertThat(reset.feeRate()).isNull();
        assertThat(positions.findByPortfolioFund(fund.id()).orElseThrow().costPerShare())
                .isEqualByComparingTo("1.25");
        assertThat(lots.findByPortfolioFund(fund.id())).singleElement()
                .satisfies(lot -> {
                    assertThat(lot.id()).isEqualTo(initialLot.id());
                    assertThat(lot.acquireTransactionId()).isEqualTo(initialTransaction.id());
                    assertThat(lot.acquireShares()).isEqualByComparingTo("100.00");
                    assertThat(lot.remainingShares()).isEqualByComparingTo("80.00");
                    assertThat(lot.acquireCostPerShare()).isEqualByComparingTo("1.10");
                });
        var afterReturns = accountingReturns.findByOwner(testActorId()).stream()
                .filter(value -> value.portfolioFundId() == fund.id()).findFirst().orElseThrow();
        assertThat(afterReturns.investedAmount()).isEqualByComparingTo(beforeReturns.investedAmount());
        assertThat(afterReturns.redeemedAmount()).isEqualByComparingTo(beforeReturns.redeemedAmount());
        assertThat(afterReturns.realizedPnl()).isEqualByComparingTo(beforeReturns.realizedPnl());
        assertThat(afterReturns.realizedComplete()).isEqualTo(beforeReturns.realizedComplete());
    }

    @Test
    void blocksPendingThenVoidsWithoutDeletingLedgerAndRetryKeepsFirstAudit() throws Exception {
        var fund = track("009999", "录入错误基金");
        classifications.set(new DisciplineClassificationApi.SetClassification(
                testActorId(), fund.id(), DisciplineClassificationApi.Category.BROAD_BASE,
                DisciplineClassificationApi.Source.USER_CONFIRMED));
        assertThat(jdbcTemplate.queryForMap("""
                SELECT category, source FROM discipline_classification WHERE portfolio_fund_id = ?
                """, fund.id()))
                .containsEntry("category", "BROAD_BASE")
                .containsEntry("source", "USER_CONFIRMED");
        LedgerTransaction pending = ledgerTransactions.save(LedgerTransaction.placePending(
                fund.id(), testActorId(), TransactionSource.INCREASE, BigDecimal.ONE, null,
                Instant.parse("2026-08-01T00:00:00Z"), null, null));

        mockMvc.perform(post("/api/portfolio-funds/{id}/void", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"基金代码录入错误\",\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS"));

        pending.cancel(Instant.parse("2026-08-02T00:00:00Z"));
        ledgerTransactions.save(pending);
        entityManager.flush();

        mockMvc.perform(post("/api/portfolio-funds/{id}/void", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"基金代码录入错误\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.voidReason").value("基金代码录入错误"));

        assertThat(ledgerTransactions.findById(pending.id())).isPresent();
        assertThat(portfolioFundApi.findOwned(testActorId(), fund.id()))
                .get().extracting(PortfolioFundApi.PortfolioFund::validity)
                .isEqualTo(PortfolioFundApi.Validity.VOIDED);

        mockMvc.perform(post("/api/portfolio-funds/{id}/void", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第二次原因\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false))
                .andExpect(jsonPath("$.data.voidReason").value("基金代码录入错误"));
    }

    @Test
    void costBasisEndpointEnforcesOwnershipVoidedAndOpenBoundaries() throws Exception {
        var fund = track("009997", "成本边界测试基金");

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":1.25}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_OPEN"));

        var other = users.create(new CurrentActorApi.Actor(testActorId(), CurrentActorApi.ActorRole.ADMIN, true),
                new UserAdministrationApi.CreateUserRequest("cost-other-" + UUID.randomUUID(),
                        "integration-test-password", UserAdministrationApi.Role.USER));
        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .cookie(cookie(other.id(), UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":1.25}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));

        portfolioFundApi.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(
                testActorId(), fund.id(), testActorId(), "测试作废",
                Instant.parse("2026-09-04T00:00:00Z")));
        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .cookie(cookie(testActorId(), UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":1.25}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));
    }

    @Test
    void costBasisResetKeepsFractionalSharesConsistentAcrossResponsePositionAndReplay() throws Exception {
        var fund = track("009996", "小数份额成本测试基金");
        Instant occurredAt = Instant.parse("2026-08-01T00:00:00Z");
        BigDecimal shares = new BigDecimal("0.01");
        LedgerTransaction initial = ledgerTransactions.save(LedgerTransaction.recordExistingPosition(
                fund.id(), testActorId(), shares, BigDecimal.ONE, occurredAt, occurredAt));
        Position position = Position.empty(fund.id(), testActorId());
        position.reconcile(true, shares, occurredAt);
        position.applyExistingPosition(BigDecimal.ONE, occurredAt);
        positions.save(position);

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":1.23456789}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPerShare").value(1.234568));

        var confirmed = ledgerTransactions.findByPortfolioFundAndStatus(
                fund.id(), com.fundpilot.backend.accounting.domain.transaction.TransactionStatus.CONFIRMED);
        LedgerTransaction reset = confirmed.stream()
                .filter(transaction -> transaction.source()
                        == com.fundpilot.backend.accounting.domain.transaction.TransactionSource.COST_BASIS_RESET)
                .findFirst().orElseThrow();
        assertThat(reset.amount()).isEqualByComparingTo("0.01234568");
        assertThat(positions.findByPortfolioFund(fund.id()).orElseThrow().costPerShare())
                .isEqualByComparingTo("1.234568");
        assertThat(LedgerReplay.replayCostPerShare(confirmed)).hasValueSatisfying(value ->
                assertThat(value).isEqualByComparingTo("1.234568"));
        assertThat(confirmed).extracting(LedgerTransaction::id).containsExactlyInAnyOrder(initial.id(), reset.id());
    }

    @Test
    void rejectsCostPerShareOverflowWithoutCreatingAuditTransaction() throws Exception {
        var fund = seedOpenPosition("009995", "成本单价溢出测试基金", new BigDecimal("1.00"));

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":100000000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));

        assertThat(ledgerTransactions.findByPortfolioFundAndStatus(
                fund.id(), com.fundpilot.backend.accounting.domain.transaction.TransactionStatus.CONFIRMED))
                .hasSize(1);
        assertThat(positions.findByPortfolioFund(fund.id()).orElseThrow().costPerShare())
                .isEqualByComparingTo("1.00");
    }

    @Test
    void rejectsTotalCostOverflowWithoutCreatingAuditTransaction() throws Exception {
        var fund = seedOpenPosition("009994", "总成本溢出测试基金", new BigDecimal("100.00"));

        mockMvc.perform(put("/api/portfolio-funds/{id}/cost-basis", fund.id())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPerShare\":1000000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_PER_SHARE_INVALID"));

        assertThat(ledgerTransactions.findByPortfolioFundAndStatus(
                fund.id(), com.fundpilot.backend.accounting.domain.transaction.TransactionStatus.CONFIRMED))
                .hasSize(1);
        assertThat(positions.findByPortfolioFund(fund.id()).orElseThrow().costPerShare())
                .isEqualByComparingTo("1.00");
    }

    private PortfolioFundApi.PortfolioFund seedOpenPosition(String code, String name, BigDecimal shares) {
        var fund = track(code, name);
        Instant occurredAt = Instant.parse("2026-08-01T00:00:00Z");
        ledgerTransactions.save(LedgerTransaction.recordExistingPosition(
                fund.id(), testActorId(), shares, BigDecimal.ONE, occurredAt, occurredAt));
        Position position = Position.empty(fund.id(), testActorId());
        position.reconcile(true, shares, occurredAt);
        position.applyExistingPosition(BigDecimal.ONE, occurredAt);
        positions.save(position);
        return fund;
    }

    private PortfolioFundApi.PortfolioFund track(String code, String name) {
        var product = products.ensure(new FundProductApi.EnsureProduct(
                code, name, null, FundProductApi.InvestmentTarget.STOCK));
        return portfolioFundApi.track(new PortfolioFundApi.TrackPortfolioFund(
                null, testActorId(), product.id(), true, new BigDecimal("0.30")));
    }

    private Cookie cookie(long ownerId, UserRole role) {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, sessions.issue(ownerId, role, 0L));
    }
}
