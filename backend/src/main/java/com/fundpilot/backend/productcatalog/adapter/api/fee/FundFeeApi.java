package com.fundpilot.backend.productcatalog.adapter.api.fee;

import com.fundpilot.backend.productcatalog.application.query.feequery.FundFeeQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundFeeApi {
    private final FundFeeQueryHandler queries;

    public Optional<FeeSchedule> findByFundCode(String fundCode) {
        return queries.findByFundCode(fundCode).map(FundFeeApi::from);
    }

    private static FeeSchedule from(FundFeeQueryHandler.FeeResult result) {
        return new FeeSchedule(result.purchaseRate(), result.discountRate(), result.salesServiceFee(),
                result.redemptionTiers().stream().map(tier ->
                        new RedemptionTier(tier.maxDays(), tier.rate())).toList(), result.fetchedAt());
    }

    public record FeeSchedule(BigDecimal purchaseRate, BigDecimal discountRate,
                              BigDecimal salesServiceFee, List<RedemptionTier> redemptionLadder,
                              Instant fetchedAt) {}
    public record RedemptionTier(Integer maxDays, BigDecimal rate) {}
}
