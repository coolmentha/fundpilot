package com.fundpilot.backend.productcatalog.application.query.feequery;

import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FundFeeQueryHandler {
    private final FundFeeScheduleRepository schedules;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<FeeResult> findByFundCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) return Optional.empty();
        return schedules.findByFundCode(fundCode.trim()).map(value -> FeeResult.from(value, clock.instant()));
    }

    public record FeeResult(BigDecimal purchaseRate, BigDecimal discountRate,
                            BigDecimal salesServiceFee, List<RedemptionTierResult> redemptionTiers,
                            BigDecimal managementFee, BigDecimal custodyFee,
                            String purchaseStatus,
                            BigDecimal purchaseLimit, BigDecimal minimumPurchaseAmount,
                            String channelReferenceLabel, String sourceName, String sourceUrl,
                            String status, boolean stale, Instant fetchedAt) {
        static FeeResult from(FundFeeSchedule schedule, Instant now) {
            return new FeeResult(schedule.purchaseRate(), schedule.discountRate(), schedule.salesServiceFee(),
                    schedule.redemptionTiers().stream().map(tier ->
                            new RedemptionTierResult(tier.maxDays(), tier.rate())).toList(),
                    schedule.managementFee(), schedule.custodyFee(), schedule.purchaseStatus() == null
                            ? null : schedule.purchaseStatus().name(),
                    schedule.purchaseLimit(), schedule.minimumPurchaseAmount(), schedule.channelReferenceLabel(),
                    schedule.sourceName(), schedule.sourceUrl(), schedule.refreshStatus().name(),
                    Duration.between(schedule.fetchedAt(), now).compareTo(Duration.ofDays(1)) > 0,
                    schedule.fetchedAt());
        }
    }
    public record RedemptionTierResult(Integer maxDays, BigDecimal rate) {}
}
