package com.fundpilot.backend.productcatalog.application.gateway.feerefresh;

import java.math.BigDecimal;
import java.util.List;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.PurchaseStatus;

public interface FundFeeSourceGateway {
    SourceFee fetch(String fundCode);

    record SourceFee(BigDecimal purchaseRate, BigDecimal discountRate,
                     BigDecimal salesServiceFee, List<SourceRedemptionTier> redemptionTiers,
                     BigDecimal managementFee, BigDecimal custodyFee,
                     PurchaseStatus purchaseStatus, BigDecimal purchaseLimit,
                     BigDecimal minimumPurchaseAmount, String channelReferenceLabel,
                     String sourceName, String sourceUrl) {}
    record SourceRedemptionTier(Integer maxDays, BigDecimal rate) {}
}
