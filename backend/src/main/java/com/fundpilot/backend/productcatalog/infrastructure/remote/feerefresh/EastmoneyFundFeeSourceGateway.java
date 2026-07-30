package com.fundpilot.backend.productcatalog.infrastructure.remote.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EastmoneyFundFeeSourceGateway implements FundFeeSourceGateway {
    private final EastmoneyFundFeeClient client;

    @Override
    public SourceFee fetch(String fundCode) {
        String html = client.fetchFeeHtml(fundCode);
        FundFeeHtmlParser.PurchaseFeeRate purchase = FundFeeHtmlParser.parsePurchaseRate(html);
        List<SourceRedemptionTier> tiers = FundFeeHtmlParser.parseRedemptionLadder(html);
        var salesServiceFee = FundFeeHtmlParser.parseSalesServiceFee(html);
        if (purchase == null && tiers.isEmpty() && salesServiceFee == null) return null;
        return new SourceFee(purchase == null ? null : purchase.originalRate(),
                purchase == null ? null : purchase.discountRate(), salesServiceFee, tiers);
    }
}
