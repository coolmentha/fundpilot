package com.fundpilot.backend.accounting.infrastructure.gateway.transactionconfirmation;

import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementFeeGateway;
import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.productcatalog.adapter.api.fee.FundFeeApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the ProductCatalog fee snapshot used when an Accounting transaction settles. */
@Component
@RequiredArgsConstructor
public class SettlementFeeGatewayImpl implements SettlementFeeGateway {
    private final FundProductApi products;
    private final FundFeeApi fees;

    @Override
    public FeeSchedule feeScheduleOf(long fundProductId) {
        return products.findById(fundProductId)
                .flatMap(product -> fees.findByFundCode(product.fundCode()))
                .map(schedule -> new FeeSchedule(schedule.discountRate(),
                        schedule.redemptionLadder().stream()
                                .map(tier -> new FeeSchedule.RedemptionTier(tier.maxDays(), tier.rate()))
                                .toList()))
                .orElseGet(FeeSchedule::none);
    }
}
