package com.fundpilot.backend.insights.application.query.portfolioreturn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnCompositionGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioReturnQueryHandlerTest {
    @Test
    void findByOwner_excludesVoidedFundsAndCalculatesReturnFromAccountingAndNavFacts() {
        ReturnCompositionGateway facts = mock(ReturnCompositionGateway.class);
        when(facts.findPortfolioFunds(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.PortfolioFund(12L, 101L, 31L, true, true, new BigDecimal("0.3")),
                new ReturnCompositionGateway.PortfolioFund(13L, 102L, 32L, false, true, new BigDecimal("0.3"))));
        when(facts.findPositions(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.Position(12L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        new BigDecimal("10"), new BigDecimal("10"))));
        when(facts.findReturnFacts(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.ReturnFact(12L, new BigDecimal("100"), BigDecimal.ZERO,
                        new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("1"), BigDecimal.ZERO, true)));
        when(facts.findProducts(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Product(31L, "000001", "示例基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE")));
        when(facts.findLatestTwoNavs(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-27T00:00:00Z"),
                        new BigDecimal("15"), new BigDecimal("15"), Instant.parse("2026-07-27T01:00:00Z"))));
        when(facts.findRealtimeValuations(Set.of("000001"))).thenReturn(List.of());
        when(facts.findGroupMemberships(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.GroupMembership(12L, 4L, "核心")));
        when(facts.findDisciplineClassifications(7L, Set.of(12L))).thenReturn(List.of(
                new ReturnCompositionGateway.DisciplineClassification(12L, "SECTOR")));

        var result = handler(facts).findByOwner(7L);

        assertThat(result.funds()).singleElement().satisfies(fund -> {
            assertThat(fund.legacyFundId()).isEqualTo(101L);
            assertThat(fund.holdingAmount()).isEqualByComparingTo("150");
            assertThat(fund.unrealizedPnl()).isEqualByComparingTo("50");
            assertThat(fund.totalReturn()).isEqualByComparingTo("50");
            assertThat(fund.disciplineCategory()).isEqualTo("SECTOR");
            assertThat(fund.groups()).containsExactly(new PortfolioReturnQueryHandler.FundGroup(4L, "核心"));
        });
        assertThat(result.investedAmount()).isEqualByComparingTo("100");
        assertThat(result.holdingAmount()).isEqualByComparingTo("150");
        assertThat(result.totalReturn()).isEqualByComparingTo("50");
    }

    @Test
    void currentAndHistorySplitByPositionStatus() {
        ReturnCompositionGateway facts = mock(ReturnCompositionGateway.class);
        when(facts.findPortfolioFunds(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.PortfolioFund(12L, 101L, 31L, true, true, new BigDecimal("0.3")),
                new ReturnCompositionGateway.PortfolioFund(13L, 102L, 32L, true, true, new BigDecimal("0.3"))));
        when(facts.findPositions(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.Position(12L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        new BigDecimal("10"), new BigDecimal("1")),
                new ReturnCompositionGateway.Position(13L, "CLEARED", Instant.parse("2025-01-01T00:00:00Z"),
                        null, BigDecimal.ZERO)));
        when(facts.findReturnFacts(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.ReturnFact(13L, new BigDecimal("100"), new BigDecimal("120"),
                        new BigDecimal("100"), new BigDecimal("120"), BigDecimal.ZERO,
                        new BigDecimal("20"), true)));
        when(facts.findProducts(Set.of(31L, 32L))).thenReturn(List.of(
                new ReturnCompositionGateway.Product(31L, "000001", "当前基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE"),
                new ReturnCompositionGateway.Product(32L, "000002", "已清仓基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE")));
        when(facts.findLatestTwoNavs(Set.of(31L, 32L))).thenReturn(List.of(
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-27T00:00:00Z"),
                        new BigDecimal("10"), new BigDecimal("10"), Instant.parse("2026-07-27T01:00:00Z"))));
        when(facts.findRealtimeValuations(Set.of("000001", "000002"))).thenReturn(List.of());
        when(facts.findGroupMemberships(7L)).thenReturn(List.of());
        when(facts.findDisciplineClassifications(7L, Set.of(12L, 13L))).thenReturn(List.of(
                new ReturnCompositionGateway.DisciplineClassification(12L, "BROAD_BASE"),
                new ReturnCompositionGateway.DisciplineClassification(13L, "ACTIVE")));
        var handler = handler(facts);

        assertThat(handler.currentFunds(7L)).extracting(PortfolioReturnQueryHandler.FundReturnResult::fundCode)
                .containsExactly("000001");
        assertThat(handler.clearedFunds(7L)).extracting(PortfolioReturnQueryHandler.FundReturnResult::fundCode)
                .containsExactly("000002");
        assertThat(handler.findByOwner(7L).totalReturn()).isEqualByComparingTo("30");
    }

    @Test
    void qdiiWithTwoConfirmedNavsReturnsZeroWhenFirstSeenNotToday() {
        ReturnCompositionGateway facts = openFundFacts("QDII", Instant.parse("2026-07-28T15:59:00Z"));

        var fund = handler(facts).currentFunds(7L).getFirst();

        assertThat(fund.dailyChangePct()).isZero();
        assertThat(fund.dailyPnl()).isZero();
        assertThat(fund.valuationSource()).isEqualTo("LATEST_CONFIRMED_NAV");
        assertThat(fund.holdingAmount()).isEqualByComparingTo("120");
    }

    @Test
    void summaryAggregatesOnlyOpenFundsAndPreservesUnknownDailyPnl() {
        ReturnCompositionGateway facts = openFundFacts("STOCK", Instant.parse("2026-07-29T01:00:00Z"));

        var summary = handler(facts).summary(7L);

        assertThat(summary.holdingAmountTotal()).isEqualByComparingTo("120");
        assertThat(summary.dailyPnlTotal()).isEqualByComparingTo("20");
        assertThat(summary.holdingFundCount()).isEqualTo(1);
        assertThat(summary.dailyCoveredFundCount()).isEqualTo(1);
        assertThat(summary.risingFundCount()).isEqualTo(1);
        assertThat(summary.profitableFundCount()).isEqualTo(1);
    }

    @Test
    void summary_withPartialUnreadyFundReturnsPartialDailySum() {
        ReturnCompositionGateway facts = mock(ReturnCompositionGateway.class);
        when(facts.findPortfolioFunds(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.PortfolioFund(12L, 101L, 31L, true, true, new BigDecimal("0.3")),
                new ReturnCompositionGateway.PortfolioFund(13L, 102L, 32L, true, true, new BigDecimal("0.3"))));
        when(facts.findPositions(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.Position(12L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.ONE, new BigDecimal("100")),
                new ReturnCompositionGateway.Position(13L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.ONE, new BigDecimal("100"))));
        when(facts.findReturnFacts(7L)).thenReturn(List.of());
        when(facts.findProducts(Set.of(31L, 32L))).thenReturn(List.of(
                new ReturnCompositionGateway.Product(31L, "000001", "就绪基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE"),
                new ReturnCompositionGateway.Product(32L, "000002", "未就绪基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE")));
        when(facts.findLatestTwoNavs(Set.of(31L, 32L))).thenReturn(List.of(
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-29T00:00:00Z"),
                        new BigDecimal("1.2"), new BigDecimal("1.2"), Instant.parse("2026-07-29T01:00:00Z")),
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-28T00:00:00Z"),
                        BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-07-28T01:00:00Z"))));
        when(facts.findRealtimeValuations(Set.of("000001", "000002"))).thenReturn(List.of());
        when(facts.findGroupMemberships(7L)).thenReturn(List.of());
        when(facts.findDisciplineClassifications(7L, Set.of(12L, 13L))).thenReturn(List.of());

        var summary = handler(facts).summary(7L);

        assertThat(summary.holdingAmountTotal()).isNull();
        assertThat(summary.dailyPnlTotal()).isEqualByComparingTo("20.0");
        assertThat(summary.dailyChangePct()).isEqualByComparingTo("0.2");
        assertThat(summary.totalPnlTotal()).isNull();
        assertThat(summary.holdingFundCount()).isEqualTo(2);
        assertThat(summary.dailyCoveredFundCount()).isEqualTo(1);
    }

    @Test
    void estimateFailureLeavesHoldingAndTotalPnlUnknownInsteadOfStaleNavFallback() {
        ReturnCompositionGateway facts = mock(ReturnCompositionGateway.class);
        when(facts.findPortfolioFunds(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.PortfolioFund(12L, 101L, 31L, true, true, new BigDecimal("0.3"))));
        when(facts.findPositions(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.Position(12L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.ONE, new BigDecimal("100"))));
        when(facts.findReturnFacts(7L)).thenReturn(List.of());
        when(facts.findProducts(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Product(31L, "000001", "示例基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE")));
        when(facts.findLatestTwoNavs(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-28T00:00:00Z"),
                        new BigDecimal("1.2"), new BigDecimal("1.2"), Instant.parse("2026-07-28T01:00:00Z"))));
        when(facts.findRealtimeValuations(Set.of("000001"))).thenReturn(List.of(
                new ReturnCompositionGateway.RealtimeValuation("000001", null, null, null, "TIMEOUT")));
        when(facts.findGroupMemberships(7L)).thenReturn(List.of());
        when(facts.findDisciplineClassifications(7L, Set.of(12L))).thenReturn(List.of());

        var result = handler(facts).findByOwner(7L);

        var fund = result.funds().getFirst();
        assertThat(fund.holdingAmount()).isNull();
        assertThat(fund.unrealizedPnl()).isNull();
        assertThat(fund.totalReturn()).isNull();
        assertThat(fund.estimateFetchFailed()).isTrue();
        assertThat(result.holdingAmount()).isNull();
        assertThat(result.unrealizedPnl()).isNull();
        assertThat(result.totalReturn()).isNull();
        assertThat(result.returnRate()).isNull();
    }

    @Test
    void estimateNotAttemptedKeepsLatestConfirmedNavAsPositionBaseline() {
        ReturnCompositionGateway facts = mock(ReturnCompositionGateway.class);
        when(facts.findPortfolioFunds(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.PortfolioFund(12L, 101L, 31L, true, true, new BigDecimal("0.3"))));
        when(facts.findPositions(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.Position(12L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.ONE, new BigDecimal("100"))));
        when(facts.findReturnFacts(7L)).thenReturn(List.of());
        when(facts.findProducts(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Product(31L, "000001", "示例基金", "ETF", "STOCK", "000300",
                        "BROAD_BASE")));
        when(facts.findLatestTwoNavs(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-28T00:00:00Z"),
                        new BigDecimal("1.2"), new BigDecimal("1.2"), Instant.parse("2026-07-28T01:00:00Z")),
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-27T00:00:00Z"),
                        BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-07-27T01:00:00Z"))));
        when(facts.findRealtimeValuations(Set.of("000001"))).thenReturn(List.of());
        when(facts.findGroupMemberships(7L)).thenReturn(List.of());
        when(facts.findDisciplineClassifications(7L, Set.of(12L))).thenReturn(List.of());

        var fund = handler(facts).currentFunds(7L).getFirst();

        assertThat(fund.holdingAmount()).isEqualByComparingTo("120");
        assertThat(fund.estimateFetchFailed()).isFalse();
        assertThat(fund.valuationSource()).isEqualTo("LATEST_CONFIRMED_NAV");
    }

    private static ReturnCompositionGateway openFundFacts(String investmentTarget, Instant firstSeenAt) {
        ReturnCompositionGateway facts = mock(ReturnCompositionGateway.class);
        when(facts.findPortfolioFunds(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.PortfolioFund(12L, 101L, 31L, true, true, new BigDecimal("0.3"))));
        when(facts.findPositions(7L)).thenReturn(List.of(
                new ReturnCompositionGateway.Position(12L, "OPEN", Instant.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.ONE, new BigDecimal("100"))));
        when(facts.findReturnFacts(7L)).thenReturn(List.of());
        when(facts.findProducts(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Product(31L, "000001", "示例基金", "ETF", investmentTarget,
                        "000300", "BROAD_BASE")));
        when(facts.findLatestTwoNavs(Set.of(31L))).thenReturn(List.of(
                new ReturnCompositionGateway.Nav(31L, Instant.parse("QDII".equals(investmentTarget)
                        ? "2026-07-28T00:00:00Z" : "2026-07-29T00:00:00Z"),
                        new BigDecimal("1.2"), new BigDecimal("1.2"), firstSeenAt),
                new ReturnCompositionGateway.Nav(31L, Instant.parse("2026-07-27T00:00:00Z"),
                        BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-07-27T01:00:00Z"))));
        when(facts.findRealtimeValuations(Set.of("000001"))).thenReturn(List.of());
        when(facts.findGroupMemberships(7L)).thenReturn(List.of());
        when(facts.findDisciplineClassifications(7L, Set.of(12L))).thenReturn(List.of());
        return facts;
    }

    private static PortfolioReturnQueryHandler handler(ReturnCompositionGateway facts) {
        return new PortfolioReturnQueryHandler(facts,
                Clock.fixed(Instant.parse("2026-07-29T06:00:00Z"), ZoneOffset.UTC));
    }
}
