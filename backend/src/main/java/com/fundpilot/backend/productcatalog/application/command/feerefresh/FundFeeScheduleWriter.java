package com.fundpilot.backend.productcatalog.application.command.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway.SourceFee;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import com.fundpilot.backend.productcatalog.domain.fee.RedemptionFeeTier;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class FundFeeScheduleWriter {
    private final FundFeeScheduleRepository schedules;

    @Transactional
    FundFeeSchedule write(String fundCode, SourceFee source, Instant fetchedAt) {
        var tiers = source.redemptionTiers() == null ? null : source.redemptionTiers().stream()
                .map(tier -> new RedemptionFeeTier(tier.maxDays(), tier.rate())).toList();
        FundFeeSchedule schedule = schedules.findByFundCode(fundCode)
                .orElseGet(() -> FundFeeSchedule.create(fundCode, source.purchaseRate(),
                        source.discountRate(), source.salesServiceFee(), tiers, source.managementFee(),
                        source.custodyFee(), source.purchaseStatus(), source.purchaseLimit(),
                        source.minimumPurchaseAmount(), source.channelReferenceLabel(),
                        source.sourceName(), source.sourceUrl(), fetchedAt));
        schedule.refresh(source.purchaseRate(), source.discountRate(), source.salesServiceFee(),
                tiers, source.managementFee(), source.custodyFee(), source.purchaseStatus(),
                source.purchaseLimit(), source.minimumPurchaseAmount(), source.channelReferenceLabel(),
                source.sourceName(), source.sourceUrl(), fetchedAt);
        return schedules.save(schedule);
    }

    @Transactional
    void markFailed(String fundCode) {
        schedules.findByFundCode(fundCode).ifPresent(schedule -> {
            schedule.markFailed();
            schedules.save(schedule);
        });
    }
}
