package com.fundpilot.backend.fund.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * legacy Accounting 内部费率快照，由 ProductCatalog 公开 API 映射后用于交易确认。
 *
 * @param discountRate      优惠申购费率(小数,如 0.0015 表 0.15%),买入扣费用此;{@code null} 表费率缺失
 * @param redemptionLadder  赎回费率阶梯(按持有期升序);空列表表费率缺失,卖出不扣赎回费
 * @param salesServiceFee   销售服务费率年化(小数,C类非0);{@code null} 表未爬到
 */
public record FundFeeSnapshot(
        BigDecimal discountRate,
        List<RedemptionTier> redemptionLadder,
        BigDecimal salesServiceFee
) {
    /** 费率缺失(fund_fee 无记录或爬取失败)时返此,调用方降级为不扣费。 */
    public static FundFeeSnapshot empty() {
        return new FundFeeSnapshot(null, List.of(), null);
    }
}
