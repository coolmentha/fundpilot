package com.fundpilot.backend.productcatalog.application.gateway.feerefresh;

import java.math.BigDecimal;
import java.util.List;

public interface FundFeeSourceGateway {
    SourceFee fetch(String fundCode);

    record SourceFee(BigDecimal purchaseRate, BigDecimal discountRate,
                     BigDecimal salesServiceFee, List<SourceRedemptionTier> redemptionTiers) {}
    record SourceRedemptionTier(Integer maxDays, BigDecimal rate) {}
}
