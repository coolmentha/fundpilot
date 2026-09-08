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
    private BigDecimal managementFee;
    private BigDecimal custodyFee;
    private PurchaseStatus purchaseStatus;
    private BigDecimal purchaseLimit;
    private BigDecimal minimumPurchaseAmount;
    private String channelReferenceLabel;
    private String sourceName;
    private String sourceUrl;
    private RefreshStatus refreshStatus;
    private List<RedemptionFeeTier> redemptionTiers;
    private Instant fetchedAt;

    private FundFeeSchedule(Long id, String fundCode, BigDecimal purchaseRate,
                            BigDecimal discountRate, BigDecimal salesServiceFee,
                            List<RedemptionFeeTier> redemptionTiers, BigDecimal managementFee,
                            BigDecimal custodyFee, PurchaseStatus purchaseStatus,
                            BigDecimal purchaseLimit, BigDecimal minimumPurchaseAmount,
                            String channelReferenceLabel, String sourceName, String sourceUrl,
                            RefreshStatus refreshStatus, Instant fetchedAt) {
        this.id = id;
        this.fundCode = requireCode(fundCode);
        this.redemptionTiers = List.of();
        refresh(purchaseRate, discountRate, salesServiceFee, redemptionTiers, managementFee,
                custodyFee, purchaseStatus, purchaseLimit, minimumPurchaseAmount,
                channelReferenceLabel, sourceName, sourceUrl, fetchedAt);
        this.refreshStatus = refreshStatus == null ? RefreshStatus.SUCCESS : refreshStatus;
    }

    public static FundFeeSchedule create(String fundCode, BigDecimal purchaseRate,
                                         BigDecimal discountRate, BigDecimal salesServiceFee,
                                         List<RedemptionFeeTier> redemptionTiers, Instant fetchedAt) {
        return new FundFeeSchedule(null, fundCode, purchaseRate, discountRate, salesServiceFee,
                redemptionTiers, null, null, null, null, null, null, null, null,
                RefreshStatus.SUCCESS, fetchedAt);
    }

    public static FundFeeSchedule create(String fundCode, BigDecimal purchaseRate,
                                         BigDecimal discountRate, BigDecimal salesServiceFee,
                                         List<RedemptionFeeTier> redemptionTiers,
                                         BigDecimal managementFee, BigDecimal custodyFee,
                                         PurchaseStatus purchaseStatus, BigDecimal purchaseLimit,
                                         BigDecimal minimumPurchaseAmount, String channelReferenceLabel,
                                         String sourceName, String sourceUrl, Instant fetchedAt) {
        return new FundFeeSchedule(null, fundCode, purchaseRate, discountRate, salesServiceFee,
                redemptionTiers, managementFee, custodyFee, purchaseStatus, purchaseLimit,
                minimumPurchaseAmount, channelReferenceLabel, sourceName, sourceUrl,
                RefreshStatus.SUCCESS, fetchedAt);
    }

    public static FundFeeSchedule rehydrate(Long id, String fundCode, BigDecimal purchaseRate,
                                            BigDecimal discountRate, BigDecimal salesServiceFee,
                                            List<RedemptionFeeTier> redemptionTiers, Instant fetchedAt) {
        return new FundFeeSchedule(Objects.requireNonNull(id), fundCode, purchaseRate, discountRate,
                salesServiceFee, redemptionTiers, null, null, null, null, null,
                null, null, null, RefreshStatus.SUCCESS, fetchedAt);
    }

    public static FundFeeSchedule rehydrate(Long id, String fundCode, BigDecimal purchaseRate,
                                            BigDecimal discountRate, BigDecimal salesServiceFee,
                                            List<RedemptionFeeTier> redemptionTiers,
                                            BigDecimal managementFee, BigDecimal custodyFee,
                                            PurchaseStatus purchaseStatus, BigDecimal purchaseLimit,
                                            BigDecimal minimumPurchaseAmount, String channelReferenceLabel,
                                            String sourceName, String sourceUrl, RefreshStatus refreshStatus,
                                            Instant fetchedAt) {
        return new FundFeeSchedule(Objects.requireNonNull(id), fundCode, purchaseRate, discountRate,
                salesServiceFee, redemptionTiers, managementFee, custodyFee, purchaseStatus,
                purchaseLimit, minimumPurchaseAmount, channelReferenceLabel, sourceName, sourceUrl,
                refreshStatus, fetchedAt);
    }

    public void refresh(BigDecimal purchaseRate, BigDecimal discountRate,
                        BigDecimal salesServiceFee, List<RedemptionFeeTier> redemptionTiers,
                        Instant fetchedAt) {
        refresh(purchaseRate, discountRate, salesServiceFee, redemptionTiers, null, null,
                null, null, null, null, null, null, fetchedAt);
    }

    public void refresh(BigDecimal purchaseRate, BigDecimal discountRate,
                        BigDecimal salesServiceFee, List<RedemptionFeeTier> redemptionTiers,
                        BigDecimal managementFee, BigDecimal custodyFee,
                        PurchaseStatus purchaseStatus, BigDecimal purchaseLimit,
                        BigDecimal minimumPurchaseAmount, String channelReferenceLabel,
                        String sourceName, String sourceUrl, Instant fetchedAt) {
        if (purchaseRate != null) this.purchaseRate = nonNegative(purchaseRate, "原申购费率");
        if (discountRate != null) this.discountRate = nonNegative(discountRate, "优惠申购费率");
        if (salesServiceFee != null) this.salesServiceFee = nonNegative(salesServiceFee, "销售服务费率");
        if (redemptionTiers != null) this.redemptionTiers = List.copyOf(redemptionTiers);
        if (managementFee != null) this.managementFee = nonNegative(managementFee, "管理费率");
        if (custodyFee != null) this.custodyFee = nonNegative(custodyFee, "托管费率");
        if (purchaseStatus != null) this.purchaseStatus = purchaseStatus;
        if (purchaseLimit != null) this.purchaseLimit = nonNegative(purchaseLimit, "申购限额");
        if (minimumPurchaseAmount != null) this.minimumPurchaseAmount = nonNegative(minimumPurchaseAmount, "最低申购金额");
        if (channelReferenceLabel != null) this.channelReferenceLabel = channelReferenceLabel;
        if (sourceName != null) this.sourceName = sourceName;
        if (sourceUrl != null) this.sourceUrl = sourceUrl;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "费率抓取时间不能为空");
        this.refreshStatus = RefreshStatus.SUCCESS;
    }

    public void markFailed() { refreshStatus = RefreshStatus.FAILED; }

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
    public BigDecimal managementFee() { return managementFee; }
    public BigDecimal custodyFee() { return custodyFee; }
    public PurchaseStatus purchaseStatus() { return purchaseStatus; }
    public BigDecimal purchaseLimit() { return purchaseLimit; }
    public BigDecimal minimumPurchaseAmount() { return minimumPurchaseAmount; }
    public String channelReferenceLabel() { return channelReferenceLabel; }
    public String sourceName() { return sourceName; }
    public String sourceUrl() { return sourceUrl; }
    public RefreshStatus refreshStatus() { return refreshStatus; }
    public List<RedemptionFeeTier> redemptionTiers() { return redemptionTiers; }
    public Instant fetchedAt() { return fetchedAt; }

    public enum PurchaseStatus { OPEN, SUSPENDED, LIMITED, UNKNOWN }
    public enum RefreshStatus { SUCCESS, FAILED }
}
