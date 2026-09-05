package com.fundpilot.backend.insights.application.query.portfolioreturn;

import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioReturnTrendQueryHandler {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private final PortfolioReturnSnapshotRepository snapshots;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TrendResult find(long ownerId, String period, Instant customFrom, Instant customTo) {
        Instant today = BusinessDay.toDateLabel(clock.instant());
        Instant to = customTo != null ? customTo : today;
        Instant from = customFrom != null ? customFrom : switch (period == null ? "30D" : period) {
            case "TODAY" -> today;
            case "7D" -> today.minus(6, ChronoUnit.DAYS);
            case "YTD" -> Instant.parse(today.atZone(java.time.ZoneOffset.UTC).getYear() + "-01-01T00:00:00Z");
            default -> today.minus(29, ChronoUnit.DAYS);
        };
        var rows = snapshots.between(ownerId, from, to);
        var baseline = snapshots.latestBefore(ownerId, from).orElse(null);
        if (rows.isEmpty()) return TrendResult.empty();
        var first = rows.getFirst();
        var last = rows.getLast();
        var start = baseline != null ? baseline : first;
        List<String> missingFundCodes = rows.stream()
                .flatMap(row -> split(row.missingFundCodes()).stream()).distinct().sorted().toList();
        BigDecimal intervalReturn = last.totalReturn().subtract(start.totalReturn());
        BigDecimal invested = last.investedAmount().subtract(start.investedAmount());
        BigDecimal redeemed = last.redeemedAmount().subtract(start.redeemedAmount());
        BigDecimal fees = last.feeAmount().subtract(start.feeAmount());
        BigDecimal denominator = start.holdingAmount().add(invested);
        BigDecimal peak = start.totalReturn();
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        for (var row : rows) {
            peak = peak.max(row.totalReturn());
            maximumDrawdown = maximumDrawdown.max(peak.subtract(row.totalReturn()));
        }
        return new TrendResult(first.businessDate(), last.businessDate(), baseline != null,
                rows.stream().allMatch(PortfolioReturnSnapshot::valuationComplete),
                missingFundCodes, intervalReturn,
                denominator.signum() > 0 ? intervalReturn.divide(denominator, MATH) : null,
                invested, redeemed, fees,
                rows.stream().map(PortfolioReturnSnapshot::totalReturn)
                        .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO), maximumDrawdown,
                rows.stream().map(row -> new TrendPoint(row.businessDate(), row.totalReturn(),
                        row.holdingAmount(), row.investedAmount(), row.redeemedAmount(),
                        row.valuationComplete())).toList());
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
    }

    public record TrendResult(Instant dataStartDate, Instant latestDate, boolean dataSufficient,
                              boolean valuationComplete, List<String> missingFundCodes,
                              BigDecimal intervalReturn, BigDecimal intervalReturnRate,
                              BigDecimal investedAmount, BigDecimal redeemedAmount, BigDecimal feeAmount,
                              BigDecimal maximumReturn, BigDecimal maximumDrawdown, List<TrendPoint> points) {
        static TrendResult empty() {
            return new TrendResult(null, null, false, true, List.of(), null, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, null, null, List.of());
        }
    }
    public record TrendPoint(Instant date, BigDecimal totalReturn, BigDecimal holdingAmount,
                             BigDecimal investedAmount, BigDecimal redeemedAmount, boolean valuationComplete) {}
}
