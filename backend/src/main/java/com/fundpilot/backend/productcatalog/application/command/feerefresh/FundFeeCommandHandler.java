package com.fundpilot.backend.productcatalog.application.command.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundFeeCommandHandler {
    private static final Logger log = LoggerFactory.getLogger(FundFeeCommandHandler.class);
    private final FundFeeScheduleRepository schedules;
    private final FundFeeSourceGateway source;
    private final FundFeeScheduleWriter writer;
    private final Clock clock;

    public Optional<FeeResult> refresh(String fundCode) {
        String code = normalizeCode(fundCode);
        final FundFeeSourceGateway.SourceFee fetched;
        try {
            fetched = source.fetch(code);
        } catch (RuntimeException exception) {
            log.warn("基金 {} 费率刷新失败", code, exception);
            return Optional.empty();
        }
        if (fetched == null) {
            log.warn("基金 {} 费率页解析全部为空,保留已有费率", code);
            return Optional.empty();
        }
        return Optional.of(FeeResult.from(writer.write(code, fetched, clock.instant())));
    }

    public Optional<FeeResult> findOrRefresh(String fundCode) {
        String code = normalizeCode(fundCode);
        return schedules.findByFundCode(code).map(FeeResult::from).or(() -> refresh(code));
    }

    public int refreshKnownSchedules() {
        int refreshed = 0;
        for (String fundCode : schedules.findAllFundCodes()) {
            if (refresh(fundCode).isPresent()) refreshed++;
        }
        return refreshed;
    }

    private String normalizeCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            throw new FundFeeFailure(FundFeeFailure.Code.FUND_FEE_INPUT_INVALID, "基金代码不能为空");
        }
        return fundCode.trim();
    }

    public record FeeResult(java.math.BigDecimal purchaseRate, java.math.BigDecimal discountRate,
                            java.math.BigDecimal salesServiceFee,
                            java.util.List<RedemptionTierResult> redemptionTiers,
                            java.time.Instant fetchedAt) {
        static FeeResult from(com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule schedule) {
            return new FeeResult(schedule.purchaseRate(), schedule.discountRate(), schedule.salesServiceFee(),
                    schedule.redemptionTiers().stream().map(tier ->
                            new RedemptionTierResult(tier.maxDays(), tier.rate())).toList(), schedule.fetchedAt());
        }
    }
    public record RedemptionTierResult(Integer maxDays, java.math.BigDecimal rate) {}
}
