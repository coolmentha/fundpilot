package com.fundpilot.backend.portfolio.domain.portfoliofund;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PortfolioFund {
    private static final int POSITION_WARNING_RATIO_SCALE = 8;
    private static final int POSITION_WARNING_RATIO_PRECISION = 19;

    private final Long id;
    private final Long legacyFundId;
    private final long ownerId;
    private final long fundProductId;
    private PortfolioFundValidity validity;
    private boolean positionWarningEnabled;
    private BigDecimal positionWarningRatio;
    private Instant voidedAt;
    private Long voidedBy;
    private String voidReason;

    private PortfolioFund(Long id, Long legacyFundId, long ownerId, long fundProductId,
                          PortfolioFundValidity validity, boolean positionWarningEnabled,
                          BigDecimal positionWarningRatio, Instant voidedAt, Long voidedBy,
                          String voidReason) {
        this.id = id;
        this.legacyFundId = legacyFundId;
        this.ownerId = requirePositive(ownerId, "用户 ID");
        this.fundProductId = requirePositive(fundProductId, "基金产品 ID");
        this.validity = Objects.requireNonNull(validity, "组合基金有效性不能为空");
        this.positionWarningEnabled = positionWarningEnabled;
        this.positionWarningRatio = requireWarningRatio(positionWarningRatio);
        this.voidedAt = voidedAt;
        this.voidedBy = voidedBy;
        this.voidReason = normalizeReason(voidReason);
        validateVoidAudit();
    }

    public static PortfolioFund createTracked(long ownerId, long fundProductId,
                                               boolean positionWarningEnabled,
                                               BigDecimal positionWarningRatio) {
        return createTracked(null, ownerId, fundProductId,
                positionWarningEnabled, positionWarningRatio);
    }

    public static PortfolioFund createTracked(Long legacyFundId, long ownerId, long fundProductId,
                                               boolean positionWarningEnabled,
                                               BigDecimal positionWarningRatio) {
        return new PortfolioFund(null, legacyFundId, ownerId, fundProductId,
                PortfolioFundValidity.TRACKED, positionWarningEnabled, positionWarningRatio,
                null, null, null);
    }

    public static PortfolioFund rehydrate(long id, Long legacyFundId, long ownerId,
                                          long fundProductId, PortfolioFundValidity validity,
                                          boolean positionWarningEnabled,
                                          BigDecimal positionWarningRatio, Instant voidedAt,
                                          Long voidedBy, String voidReason) {
        return new PortfolioFund(requirePositive(id, "组合基金 ID"), legacyFundId, ownerId,
                fundProductId, validity, positionWarningEnabled, positionWarningRatio,
                voidedAt, voidedBy, voidReason);
    }

    public Optional<PortfolioFundVoided> voidBy(long actorId, String reason, Instant occurredAt) {
        if (validity == PortfolioFundValidity.VOIDED) {
            return Optional.empty();
        }
        requirePositive(actorId, "作废操作者 ID");
        String normalizedReason = requireReason(reason);
        Objects.requireNonNull(occurredAt, "作废时间不能为空");
        if (id == null) {
            throw new IllegalStateException("未持久化的组合基金不能作废");
        }

        validity = PortfolioFundValidity.VOIDED;
        voidedAt = occurredAt;
        voidedBy = actorId;
        voidReason = normalizedReason;
        return Optional.of(new PortfolioFundVoided(
                id, ownerId, fundProductId, actorId, normalizedReason, occurredAt));
    }

    public void configurePositionWarning(boolean enabled, BigDecimal ratio) {
        if (validity == PortfolioFundValidity.VOIDED) {
            throw new IllegalStateException("作废组合基金不能修改仓位提醒");
        }
        positionWarningEnabled = enabled;
        positionWarningRatio = requireWarningRatio(ratio);
    }

    private void validateVoidAudit() {
        boolean hasCompleteAudit = voidedAt != null && voidedBy != null && voidReason != null;
        if (validity == PortfolioFundValidity.TRACKED && (voidedAt != null || voidedBy != null || voidReason != null)) {
            throw new IllegalArgumentException("有效组合基金不能包含作废审计");
        }
        if (validity == PortfolioFundValidity.VOIDED && !hasCompleteAudit) {
            throw new IllegalArgumentException("作废组合基金必须包含完整审计");
        }
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + "必须为正数");
        return value;
    }

    private static BigDecimal requireWarningRatio(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("仓位提醒比例不能为空");
        }
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("仓位提醒比例必须大于 0 且不超过 1");
        }
        BigDecimal normalized = value.setScale(POSITION_WARNING_RATIO_SCALE, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0 || normalized.precision() > POSITION_WARNING_RATIO_PRECISION) {
            throw new IllegalArgumentException("仓位提醒比例必须大于 0 且不超过 1");
        }
        return normalized;
    }

    private static String requireReason(String value) {
        String normalized = normalizeReason(value);
        if (normalized == null) throw new IllegalArgumentException("作废原因不能为空");
        return normalized;
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long id() { return id; }
    public Long legacyFundId() { return legacyFundId; }
    public long ownerId() { return ownerId; }
    public long fundProductId() { return fundProductId; }
    public PortfolioFundValidity validity() { return validity; }
    public boolean positionWarningEnabled() { return positionWarningEnabled; }
    public BigDecimal positionWarningRatio() { return positionWarningRatio; }
    public Instant voidedAt() { return voidedAt; }
    public Long voidedBy() { return voidedBy; }
    public String voidReason() { return voidReason; }
}
