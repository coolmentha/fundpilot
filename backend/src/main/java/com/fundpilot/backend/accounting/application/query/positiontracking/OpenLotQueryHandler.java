package com.fundpilot.backend.accounting.application.query.positiontracking;

import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.gateway.positiontracking.OpenLotValuationGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户持仓批次的赎回费只读估算。 */
@Service
@RequiredArgsConstructor
public class OpenLotQueryHandler {
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final TradedPortfolioFundGateway portfolioFunds;
    private final LotRepository lots;
    private final OpenLotValuationGateway valuations;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Summary find(long ownerId, long portfolioFundId) {
        var fund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .filter(TradedPortfolioFundGateway.TradedPortfolioFund::tradable)
                .orElseThrow(() -> new TransactionLedgerFailure(
                        TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: " + portfolioFundId));
        List<Lot> openLots = lots.findOpenLotsOrderByAcquireDate(portfolioFundId);
        if (openLots.isEmpty()) {
            return new Summary(portfolioFundId, null, null, null, UnavailableReason.NO_OPEN_LOTS.name(), List.of());
        }

        Optional<OpenLotValuationGateway.LatestNav> latestNav = valuations.latestNav(fund.fundProductId());
        Optional<OpenLotValuationGateway.RedemptionSchedule> schedule =
                valuations.redemptionSchedule(fund.fundProductId());
        Instant now = clock.instant();
        List<LotEstimate> estimates = openLots.stream().map(lot -> estimate(lot, now, latestNav, schedule)).toList();
        BigDecimal totalFee = estimates.stream().allMatch(estimate -> estimate.estimatedRedemptionFee() != null)
                ? estimates.stream().map(LotEstimate::estimatedRedemptionFee).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        String reason = estimates.stream().map(LotEstimate::unavailableReason)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return new Summary(portfolioFundId, latestNav.map(OpenLotValuationGateway.LatestNav::navDate).orElse(null),
                latestNav.map(OpenLotValuationGateway.LatestNav::unitNav).orElse(null), totalFee, reason, estimates);
    }

    private static LotEstimate estimate(Lot lot, Instant now,
            Optional<OpenLotValuationGateway.LatestNav> latestNav,
            Optional<OpenLotValuationGateway.RedemptionSchedule> schedule) {
        long holdingDays = Math.max(0, BusinessDay.daysBetween(lot.acquireDate(), now));
        Optional<BigDecimal> rate = schedule.flatMap(value -> value.rateFor(holdingDays));
        String reason = latestNav.isEmpty() ? UnavailableReason.LATEST_NAV_MISSING.name()
                : rate.isEmpty() ? UnavailableReason.REDEMPTION_FEE_MISSING.name() : null;
        BigDecimal fee = reason == null
                ? lot.remainingShares().multiply(latestNav.orElseThrow().unitNav(), MATH)
                        .multiply(rate.orElseThrow(), MATH)
                : null;
        return new LotEstimate(lot.acquireDate(), lot.remainingShares(), holdingDays, rate.orElse(null), fee, reason);
    }

    public record Summary(long portfolioFundId, Instant latestNavDate, BigDecimal latestUnitNav,
                          BigDecimal estimatedRedemptionFee, String unavailableReason,
                          List<LotEstimate> lots) {}

    public record LotEstimate(Instant acquireDate, BigDecimal remainingShares, long holdingDays,
                              BigDecimal redemptionRate, BigDecimal estimatedRedemptionFee,
                              String unavailableReason) {}

    private enum UnavailableReason { NO_OPEN_LOTS, LATEST_NAV_MISSING, REDEMPTION_FEE_MISSING }
}
