package com.fundpilot.backend.accounting.application.query.positiontracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.gateway.positiontracking.OpenLotValuationGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenLotQueryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");
    private static final Instant ACQUIRE_DATE = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NAV_DATE = Instant.parse("2026-09-07T00:00:00Z");

    @Test
    void 真实零费率返回零估算而不是资料缺失() {
        var fixture = fixture();
        when(fixture.valuations.latestNav(101L)).thenReturn(Optional.of(
                new OpenLotValuationGateway.LatestNav(NAV_DATE, new BigDecimal("1.20"))));
        when(fixture.valuations.redemptionSchedule(101L)).thenReturn(Optional.of(
                new OpenLotValuationGateway.RedemptionSchedule(List.of(
                        new OpenLotValuationGateway.RedemptionTier(null, BigDecimal.ZERO)))));

        var result = fixture.handler.find(3L, 11L);

        assertThat(result.latestNavDate()).isEqualTo(NAV_DATE);
        assertThat(result.estimatedRedemptionFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.unavailableReason()).isNull();
        assertThat(result.lots()).singleElement().satisfies(lot -> {
            assertThat(lot.holdingDays()).isEqualTo(7);
            assertThat(lot.redemptionRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(lot.estimatedRedemptionFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(lot.unavailableReason()).isNull();
        });
    }

    @Test
    void 费率或净值缺失时估算为空并说明原因() {
        var feeMissing = fixture();
        when(feeMissing.valuations.latestNav(101L)).thenReturn(Optional.of(
                new OpenLotValuationGateway.LatestNav(NAV_DATE, new BigDecimal("1.20"))));
        when(feeMissing.valuations.redemptionSchedule(101L)).thenReturn(Optional.empty());
        assertThat(feeMissing.handler.find(3L, 11L).lots()).singleElement().satisfies(lot -> {
            assertThat(lot.estimatedRedemptionFee()).isNull();
            assertThat(lot.unavailableReason()).isEqualTo("REDEMPTION_FEE_MISSING");
        });

        var navMissing = fixture();
        when(navMissing.valuations.latestNav(101L)).thenReturn(Optional.empty());
        when(navMissing.valuations.redemptionSchedule(101L)).thenReturn(Optional.of(
                new OpenLotValuationGateway.RedemptionSchedule(List.of(
                        new OpenLotValuationGateway.RedemptionTier(null, new BigDecimal("0.015"))))));
        assertThat(navMissing.handler.find(3L, 11L).lots()).singleElement().satisfies(lot -> {
            assertThat(lot.estimatedRedemptionFee()).isNull();
            assertThat(lot.unavailableReason()).isEqualTo("LATEST_NAV_MISSING");
        });
    }

    @Test
    void 非本人组合基金按不存在处理() {
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        var handler = new OpenLotQueryHandler(portfolioFunds, mock(LotRepository.class),
                mock(OpenLotValuationGateway.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> handler.find(3L, 11L))
                .isInstanceOf(TransactionLedgerFailure.class)
                .extracting(failure -> ((TransactionLedgerFailure) failure).code())
                .isEqualTo(TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    @Test
    void 无开放批次返回空列表与明确原因() {
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        LotRepository lots = mock(LotRepository.class);
        when(portfolioFunds.findOwned(3L, 11L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(11L, 3L, 101L, null, true)));
        var handler = new OpenLotQueryHandler(portfolioFunds, lots, mock(OpenLotValuationGateway.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = handler.find(3L, 11L);

        assertThat(result.lots()).isEmpty();
        assertThat(result.estimatedRedemptionFee()).isNull();
        assertThat(result.unavailableReason()).isEqualTo("NO_OPEN_LOTS");
    }

    private static Fixture fixture() {
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        LotRepository lots = mock(LotRepository.class);
        OpenLotValuationGateway valuations = mock(OpenLotValuationGateway.class);
        when(portfolioFunds.findOwned(3L, 11L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(11L, 3L, 101L, null, true)));
        when(lots.findOpenLotsOrderByAcquireDate(11L)).thenReturn(List.of(
                Lot.rehydrate(1L, 11L, 7L, ACQUIRE_DATE, new BigDecimal("100"),
                        new BigDecimal("80"), new BigDecimal("1.10"))));
        return new Fixture(valuations, new OpenLotQueryHandler(portfolioFunds, lots, valuations,
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private record Fixture(OpenLotValuationGateway valuations, OpenLotQueryHandler handler) {}
}
