package com.fundpilot.backend.alerting.domain.alertrule;

import java.math.BigDecimal;
import java.util.Objects;

/** 价格提醒规则聚合根；单基金规则（FUND）优先于全局规则（GLOBAL）。 */
public final class AlertRule {

    private static final BigDecimal MAX_THRESHOLD = BigDecimal.ONE;

    private final Long id;
    private final Long version;
    private final long ownerId;
    private AlertRuleScope scope;
    private Long portfolioFundId;
    private AlertRuleType type;
    private BigDecimal threshold;
    private boolean enabled;

    private AlertRule(Long id, Long version, long ownerId, AlertRuleScope scope, Long portfolioFundId,
                      AlertRuleType type, BigDecimal threshold, boolean enabled) {
        this.id = id;
        this.version = version;
        this.ownerId = positive(ownerId, "用户 ID");
        this.type = Objects.requireNonNull(type, "提醒类型不能为空");
        this.threshold = requireThreshold(threshold);
        this.enabled = enabled;
        applyScope(scope, portfolioFundId);
    }

    public static AlertRule create(long ownerId, AlertRuleScope scope, Long portfolioFundId,
                                   AlertRuleType type, BigDecimal threshold, boolean enabled) {
        return new AlertRule(null, null, ownerId, scope, portfolioFundId, type, threshold, enabled);
    }

    public static AlertRule rehydrate(long id, long ownerId, AlertRuleScope scope, Long portfolioFundId,
                                      AlertRuleType type, BigDecimal threshold, boolean enabled) {
        return rehydrate(id, null, ownerId, scope, portfolioFundId, type, threshold, enabled);
    }

    public static AlertRule rehydrate(long id, Long version, long ownerId, AlertRuleScope scope,
                                      Long portfolioFundId, AlertRuleType type, BigDecimal threshold,
                                      boolean enabled) {
        return new AlertRule(positive(id, "提醒规则 ID"), version, ownerId, scope, portfolioFundId, type,
                threshold, enabled);
    }

    public void update(AlertRuleScope scope, Long portfolioFundId, AlertRuleType type, BigDecimal threshold) {
        this.type = Objects.requireNonNull(type, "提醒类型不能为空");
        this.threshold = requireThreshold(threshold);
        applyScope(scope, portfolioFundId);
    }

    public void changeThreshold(BigDecimal threshold) {
        this.threshold = requireThreshold(threshold);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /** 该规则是否被观测值触发。observedValue 为小数口径（0.05 表示 5%）。 */
    public boolean triggered(BigDecimal observedValue) {
        if (observedValue == null) {
            return false;
        }
        return switch (type) {
            case RISE, PROFIT -> observedValue.compareTo(threshold) >= 0;
            case DROP -> observedValue.compareTo(threshold.negate()) <= 0;
        };
    }

    /**
     * 该规则是否适用于该基金。仅按持仓状态过滤：PROFIT 只对已持仓基金生效。
     *
     * <p>入参用基本类型而非应用层 record，避免 domain 反向依赖上层。
     */
    public boolean appliesTo(boolean positionOpen) {
        return type != AlertRuleType.PROFIT || positionOpen;
    }

    public boolean global() {
        return scope == AlertRuleScope.GLOBAL;
    }

    private void applyScope(AlertRuleScope scope, Long portfolioFundId) {
        AlertRuleScope validated = Objects.requireNonNull(scope, "规则范围不能为空");
        switch (validated) {
            case GLOBAL -> {
                this.scope = validated;
                this.portfolioFundId = null;
            }
            case FUND -> {
                if (portfolioFundId == null || portfolioFundId <= 0) {
                    throw new IllegalArgumentException("单基金规则必须指定基金");
                }
                this.scope = validated;
                this.portfolioFundId = portfolioFundId;
            }
        }
    }

    private static BigDecimal requireThreshold(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_THRESHOLD) > 0) {
            throw new IllegalArgumentException("提醒阈值必须大于 0 且不超过 1");
        }
        return value;
    }

    private static long positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + "必须为正数");
        return value;
    }

    public Long id() { return id; }
    public Long version() { return version; }
    public long ownerId() { return ownerId; }
    public AlertRuleScope scope() { return scope; }
    public Long portfolioFundId() { return portfolioFundId; }
    public AlertRuleType type() { return type; }
    public BigDecimal threshold() { return threshold; }
    public boolean enabled() { return enabled; }
}
