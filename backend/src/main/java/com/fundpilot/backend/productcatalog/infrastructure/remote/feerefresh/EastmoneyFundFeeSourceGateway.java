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
        var managementFee = FundFeeHtmlParser.parseOperationFee(html, "管理费率");
        var custodyFee = FundFeeHtmlParser.parseOperationFee(html, "托管费率");
        var terms = FundFeeHtmlParser.parsePurchaseTerms(html);
        if (purchase == null && tiers.isEmpty() && salesServiceFee == null
                && managementFee == null && custodyFee == null && terms == null) return null;
        return new SourceFee(purchase == null ? null : purchase.originalRate(),
                purchase == null ? null : purchase.discountRate(), salesServiceFee,
                tiers.isEmpty() ? null : tiers,
                managementFee, custodyFee, terms == null ? null : terms.status(),
                terms == null ? null : terms.purchaseLimit(),
                terms == null ? null : terms.minimumPurchaseAmount(),
                "天天基金公开费率，仅供参考，以实际交易渠道为准", "东方财富",
                "https://fundf10.eastmoney.com/jjfl_" + fundCode + ".html");
    }
}
