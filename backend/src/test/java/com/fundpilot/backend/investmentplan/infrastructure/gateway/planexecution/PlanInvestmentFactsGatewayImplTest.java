package com.fundpilot.backend.investmentplan.infrastructure.gateway.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.marketdata.adapter.api.indexkline.IndexKlineApi;
import com.fundpilot.backend.marketdata.adapter.api.indexvaluation.IndexValuationApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanInvestmentFactsGatewayImplTest {
    private static final Instant BUSINESS_DATE = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant PREVIOUS_NAV_DATE = Instant.parse("2026-07-24T00:00:00Z");
    private static final Instant OLDER_NAV_DATE = Instant.parse("2026-07-23T00:00:00Z");

    @Test
    void 涨跌幅策略排除业务日当日净值并使用上一确认净值() {
        PositionApi positions = mock(PositionApi.class);
        PublishedNavApi navs = mock(PublishedNavApi.class);
        when(navs.latest(101L)).thenReturn(Optional.of(nav(BUSINESS_DATE, "1.20")));
        when(navs.history(101L, Instant.EPOCH, BUSINESS_DATE))
                .thenReturn(List.of(nav(OLDER_NAV_DATE, "0.80"), nav(PREVIOUS_NAV_DATE, "0.90")));
        when(positions.findOwned(3L, 11L)).thenReturn(Optional.of(position("1.00")));

        var facts = gateway(positions, navs).load(plan(), fund(), BUSINESS_DATE).orElseThrow();

        assertThat(facts.policyFacts().nav()).isEqualByComparingTo("0.90");
        assertThat(facts.dataDate()).isEqualTo(PREVIOUS_NAV_DATE);
        verify(navs).history(101L, Instant.EPOCH, BUSINESS_DATE);
        verify(navs, never()).latest(101L);
    }

    @Test
    void 涨跌幅策略在业务日前没有确认净值时返回净值缺失() {
        PositionApi positions = mock(PositionApi.class);
        PublishedNavApi navs = mock(PublishedNavApi.class);
        when(navs.latest(101L)).thenReturn(Optional.of(nav(BUSINESS_DATE, "1.20")));
        when(navs.history(101L, Instant.EPOCH, BUSINESS_DATE)).thenReturn(List.of());
        when(positions.findOwned(3L, 11L)).thenReturn(Optional.of(position("1.00")));

        var facts = gateway(positions, navs).load(plan(), fund(), BUSINESS_DATE).orElseThrow();

        assertThat(facts.policyFacts().nav()).isNull();
        assertThat(facts.dataDate()).isNull();
        verify(navs, never()).latest(101L);
    }

    private static PlanInvestmentFactsGatewayImpl gateway(PositionApi positions, PublishedNavApi navs) {
        return new PlanInvestmentFactsGatewayImpl(positions, navs, mock(IndexKlineApi.class),
                mock(IndexValuationApi.class));
    }

    private static InvestmentPlan plan() {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.DAILY, null, null, InvestmentPlanAmountStrategy.CHANGE_RATE,
                null, null, InvestmentPlanStatus.EFFECTIVE);
    }

    private static PlanPortfolioFundGateway.PortfolioFund fund() {
        return new PlanPortfolioFundGateway.PortfolioFund(11L, 41L, 101L, null);
    }

    private static PositionApi.Position position(String costPerShare) {
        return new PositionApi.Position(11L, 3L, PositionApi.Status.OPEN, PREVIOUS_NAV_DATE,
                new BigDecimal(costPerShare), BigDecimal.TEN);
    }

    private static PublishedNavApi.PublishedNav nav(Instant navDate, String unitNav) {
        return new PublishedNavApi.PublishedNav(101L, "000001", navDate, new BigDecimal(unitNav),
                new BigDecimal(unitNav), navDate);
    }
}
