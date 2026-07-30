package com.fundpilot.backend.productcatalog.application.query.feequery;

import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FundFeeQueryHandler {
    private final FundFeeScheduleRepository schedules;

    @Transactional(readOnly = true)
    public Optional<FeeResult> findByFundCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) return Optional.empty();
        return schedules.findByFundCode(fundCode.trim()).map(FeeResult::from);
    }

    public record FeeResult(BigDecimal purchaseRate, BigDecimal discountRate,
                            BigDecimal salesServiceFee, List<RedemptionTierResult> redemptionTiers,
                            Instant fetchedAt) {
        static FeeResult from(FundFeeSchedule schedule) {
            return new FeeResult(schedule.purchaseRate(), schedule.discountRate(), schedule.salesServiceFee(),
                    schedule.redemptionTiers().stream().map(tier ->
                            new RedemptionTierResult(tier.maxDays(), tier.rate())).toList(), schedule.fetchedAt());
        }
    }
    public record RedemptionTierResult(Integer maxDays, BigDecimal rate) {}
}
