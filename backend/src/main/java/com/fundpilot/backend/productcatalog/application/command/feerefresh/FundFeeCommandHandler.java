package com.fundpilot.backend.productcatalog.application.command.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
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
    private static final int DAILY_BATCH_SIZE = 100;
    private final FundResearchRepository research;
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
            writer.markFailed(code);
            return Optional.empty();
        }
        if (fetched == null) {
            log.warn("基金 {} 费率页解析全部为空,保留已有费率", code);
            writer.markFailed(code);
            return Optional.empty();
        }
        return Optional.of(FeeResult.from(writer.write(code, fetched, clock.instant())));
    }

    public int refreshTrackedFunds() {
        int refreshed = 0;
        for (String fundCode : research.findTrackedFundCodes(DAILY_BATCH_SIZE)) {
            try {
                if (refresh(fundCode).isPresent()) refreshed++;
            } catch (RuntimeException exception) {
                log.error("基金 {} 费率写入失败，继续刷新其他基金", fundCode, exception);
            }
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
                            java.math.BigDecimal managementFee, java.math.BigDecimal custodyFee,
                            com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.PurchaseStatus purchaseStatus,
                            java.math.BigDecimal purchaseLimit, java.math.BigDecimal minimumPurchaseAmount,
                            String channelReferenceLabel, String sourceName, String sourceUrl,
                            com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.RefreshStatus status,
                            java.time.Instant fetchedAt) {
        static FeeResult from(com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule schedule) {
            return new FeeResult(schedule.purchaseRate(), schedule.discountRate(), schedule.salesServiceFee(),
                    schedule.redemptionTiers().stream().map(tier ->
                            new RedemptionTierResult(tier.maxDays(), tier.rate())).toList(),
                    schedule.managementFee(), schedule.custodyFee(), schedule.purchaseStatus(),
                    schedule.purchaseLimit(), schedule.minimumPurchaseAmount(), schedule.channelReferenceLabel(),
                    schedule.sourceName(), schedule.sourceUrl(), schedule.refreshStatus(), schedule.fetchedAt());
        }
    }
    public record RedemptionTierResult(Integer maxDays, java.math.BigDecimal rate) {}
}
