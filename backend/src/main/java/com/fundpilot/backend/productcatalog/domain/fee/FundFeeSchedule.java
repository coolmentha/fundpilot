package com.fundpilot.backend.productcatalog.domain.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class FundFeeSchedule {
    private final Long id;
    private final String fundCode;
    private BigDecimal purchaseRate;
    private BigDecimal discountRate;
    private BigDecimal salesServiceFee;
    private List<RedemptionFeeTier> redemptionTiers;
    private Instant fetchedAt;

    private FundFeeSchedule(Long id, String fundCode, BigDecimal purchaseRate,
                            BigDecimal discountRate, BigDecimal salesServiceFee,
                            List<RedemptionFeeTier> redemptionTiers, Instant fetchedAt) {
        this.id = id;
        this.fundCode = requireCode(fundCode);
        refresh(purchaseRate, discountRate, salesServiceFee, redemptionTiers, fetchedAt);
    }

    public static FundFeeSchedule create(String fundCode, BigDecimal purchaseRate,
                                         BigDecimal discountRate, BigDecimal salesServiceFee,
                                         List<RedemptionFeeTier> redemptionTiers, Instant fetchedAt) {
        return new FundFeeSchedule(null, fundCode, purchaseRate, discountRate, salesServiceFee,
                redemptionTiers, fetchedAt);
    }

    public static FundFeeSchedule rehydrate(Long id, String fundCode, BigDecimal purchaseRate,
                                            BigDecimal discountRate, BigDecimal salesServiceFee,
                                            List<RedemptionFeeTier> redemptionTiers, Instant fetchedAt) {
        return new FundFeeSchedule(Objects.requireNonNull(id), fundCode, purchaseRate, discountRate,
                salesServiceFee, redemptionTiers, fetchedAt);
    }

    public void refresh(BigDecimal purchaseRate, BigDecimal discountRate,
                        BigDecimal salesServiceFee, List<RedemptionFeeTier> redemptionTiers,
                        Instant fetchedAt) {
        this.purchaseRate = nonNegative(purchaseRate, "原申购费率");
        this.discountRate = nonNegative(discountRate, "优惠申购费率");
        this.salesServiceFee = nonNegative(salesServiceFee, "销售服务费率");
        this.redemptionTiers = List.copyOf(redemptionTiers == null ? List.of() : redemptionTiers);
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "费率抓取时间不能为空");
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("基金代码不能为空");
        return value.trim();
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) throw new IllegalArgumentException(field + "不能为负数");
        return value;
    }

    public Long id() { return id; }
    public String fundCode() { return fundCode; }
    public BigDecimal purchaseRate() { return purchaseRate; }
    public BigDecimal discountRate() { return discountRate; }
    public BigDecimal salesServiceFee() { return salesServiceFee; }
    public List<RedemptionFeeTier> redemptionTiers() { return redemptionTiers; }
    public Instant fetchedAt() { return fetchedAt; }
}
