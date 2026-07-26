package com.fundpilot.backend.portfolio.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.portfolio.controller.PortfolioReturnTrendPointView;
import com.fundpilot.backend.portfolio.controller.PortfolioReturnTrendView;
import com.fundpilot.backend.portfolio.entity.PortfolioReturnSnapshotEntity;
import com.fundpilot.backend.portfolio.repository.PortfolioReturnSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;

@Service
@RequiredArgsConstructor
public class PortfolioReturnTrendService {

    private static final MathContext MATH = MathContext.DECIMAL64;
    private final PortfolioReturnSnapshotRepository repository;
    private final PortfolioReturnService returnService;
    private final Clock clock;
    private final CurrentActorApi currentActorApi;

    @Transactional
    public void capture(Instant businessDate) {
        long userId = currentActorApi.userId();
        var returns = returnService.getReturns();
        List<String> missing = returns.funds().stream()
                .filter(fund -> fund.investedAmount().signum() > 0 && fund.unrealizedPnl() == null)
                .map(fund -> fund.fundCode()).sorted().toList();
        PortfolioReturnSnapshotEntity row = repository.findByOwnerIdAndBusinessDate(userId, businessDate)
                .orElseGet(PortfolioReturnSnapshotEntity::new);
        row.setBusinessDate(businessDate);
        row.setOwnerId(userId);
        row.setInvestedAmount(returns.investedAmount());
        row.setRedeemedAmount(returns.redeemedAmount());
        row.setFeeAmount(returns.feeAmount());
        row.setHoldingAmount(returns.holdingAmount());
        row.setRealizedPnl(returns.realizedPnl());
        row.setUnrealizedPnl(returns.unrealizedPnl());
        row.setTotalReturn(returns.totalReturn());
        row.setValuationComplete(missing.isEmpty());
        row.setMissingFundCodes(String.join(",", missing));
        row.setCapturedAt(clock.instant());
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public PortfolioReturnTrendView getTrend(String period, Instant customFrom, Instant customTo) {
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());
        Instant to = customTo != null ? customTo : today;
        Instant from = customFrom != null ? customFrom : switch (period == null ? "30D" : period) {
            case "TODAY" -> today;
            case "7D" -> today.minus(6, ChronoUnit.DAYS);
            case "YTD" -> Instant.parse(today.atZone(java.time.ZoneOffset.UTC).getYear() + "-01-01T00:00:00Z");
            default -> today.minus(29, ChronoUnit.DAYS);
        };
        long userId = currentActorApi.userId();
        List<PortfolioReturnSnapshotEntity> rows =
                repository.findByOwnerIdAndBusinessDateBetweenOrderByBusinessDateAsc(userId, from, to);
        PortfolioReturnSnapshotEntity baseline =
                repository.findTopByOwnerIdAndBusinessDateBeforeOrderByBusinessDateDesc(userId, from)
                .orElse(null);
        if (rows.isEmpty()) return empty();
        PortfolioReturnSnapshotEntity first = rows.getFirst();
        PortfolioReturnSnapshotEntity last = rows.getLast();
        PortfolioReturnSnapshotEntity start = baseline != null ? baseline : first;
        BigDecimal intervalReturn = last.getTotalReturn().subtract(start.getTotalReturn());
        BigDecimal invested = last.getInvestedAmount().subtract(start.getInvestedAmount());
        BigDecimal redeemed = last.getRedeemedAmount().subtract(start.getRedeemedAmount());
        BigDecimal fees = last.getFeeAmount().subtract(start.getFeeAmount());
        BigDecimal denominator = start.getHoldingAmount().add(invested);
        BigDecimal rate = denominator.signum() > 0 ? intervalReturn.divide(denominator, MATH) : null;
        BigDecimal peak = start.getTotalReturn();
        BigDecimal maxReturn = rows.stream().map(PortfolioReturnSnapshotEntity::getTotalReturn).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (PortfolioReturnSnapshotEntity row : rows) {
            peak = peak.max(row.getTotalReturn());
            maxDrawdown = maxDrawdown.max(peak.subtract(row.getTotalReturn()));
        }
        List<String> missing = split(last.getMissingFundCodes());
        return new PortfolioReturnTrendView(first.getBusinessDate(), last.getBusinessDate(), baseline != null,
                rows.stream().allMatch(PortfolioReturnSnapshotEntity::isValuationComplete), missing,
                intervalReturn, rate, invested, redeemed, fees, maxReturn, maxDrawdown,
                rows.stream().map(PortfolioReturnTrendPointView::from).toList());
    }

    private PortfolioReturnTrendView empty() {
        return new PortfolioReturnTrendView(null, null, false, true, List.of(), null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, List.of());
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
    }
}
