package com.fundpilot.backend.accounting.infrastructure.gateway.positiontracking;

import com.fundpilot.backend.accounting.application.gateway.positiontracking.OpenLotValuationGateway;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.productcatalog.adapter.api.fee.FundFeeApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenLotValuationGatewayImpl implements OpenLotValuationGateway {
    private final PublishedNavApi navs;
    private final FundProductApi products;
    private final FundFeeApi fees;

    @Override
    public Optional<LatestNav> latestNav(long fundProductId) {
        return navs.latest(fundProductId)
                .filter(nav -> nav.unitNav() != null && nav.unitNav().signum() > 0)
                .map(nav -> new LatestNav(nav.navDate(), nav.unitNav()));
    }

    @Override
    public Optional<RedemptionSchedule> redemptionSchedule(long fundProductId) {
        return products.findById(fundProductId)
                .flatMap(product -> fees.findByFundCode(product.fundCode()))
                .map(schedule -> new RedemptionSchedule(schedule.redemptionLadder().stream()
                        .filter(tier -> tier.rate() != null)
                        .map(tier -> new RedemptionTier(tier.maxDays(), tier.rate()))
                        .toList()));
    }
}
