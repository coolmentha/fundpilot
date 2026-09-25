package com.fundpilot.backend.alerting.domain.alertrule;

import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 提醒规则聚合根：通用规则是一组指标条件的合取（全部条件满足即触发），建议型规则在此之上带出建议卖出份额。
 *
 * <p>单基金规则（FUND）只对指定基金生效，优先级高于全局规则（GLOBAL）：全局规则会剔除已被
 * 「同一基金 + 同一条件组」的单基金规则覆盖的基金。
 */
public final class AlertRule {

    private final Long id;
    private final Long version;
    private final long ownerId;
    private AlertRuleScope scope;
    private Long portfolioFundId;
    private AlertRuleKind kind;
    private ConditionGroup conditions;
    private TakeProfitParams takeProfit;
    private boolean enabled;

    private AlertRule(Long id, Long version, long ownerId, AlertRuleScope scope, Long portfolioFundId,
                      AlertRuleKind kind, ConditionGroup conditions, TakeProfitParams takeProfit, boolean enabled) {
        this.id = id;
        this.version = version;
        this.ownerId = positive(ownerId, "用户 ID");
        apply(kind, conditions, takeProfit);
        this.enabled = enabled;
        applyScope(scope, portfolioFundId);
    }

    public static AlertRule create(long ownerId, AlertRuleScope scope, Long portfolioFundId, AlertRuleKind kind,
                                   ConditionGroup conditions, TakeProfitParams takeProfit, boolean enabled) {
        return new AlertRule(null, null, ownerId, scope, portfolioFundId, kind, conditions, takeProfit, enabled);
    }

    public static AlertRule rehydrate(long id, long ownerId, AlertRuleScope scope, Long portfolioFundId,
                                      AlertRuleKind kind, ConditionGroup conditions, TakeProfitParams takeProfit,
                                      boolean enabled) {
        return rehydrate(id, null, ownerId, scope, portfolioFundId, kind, conditions, takeProfit, enabled);
    }

    public static AlertRule rehydrate(long id, Long version, long ownerId, AlertRuleScope scope,
                                      Long portfolioFundId, AlertRuleKind kind, ConditionGroup conditions,
                                      TakeProfitParams takeProfit, boolean enabled) {
        return new AlertRule(positive(id, "提醒规则 ID"), version, ownerId, scope, portfolioFundId, kind, conditions,
                takeProfit, enabled);
    }

    public void update(AlertRuleScope scope, Long portfolioFundId, AlertRuleKind kind, ConditionGroup conditions,
                       TakeProfitParams takeProfit) {
        apply(kind, conditions, takeProfit);
        applyScope(scope, portfolioFundId);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean global() {
        return scope == AlertRuleScope.GLOBAL;
    }

    /** 建议型规则：判定不走通用条件求值器，通知包含建议卖出份额。 */
    public boolean suggestion() {
        return kind.suggestion();
    }

    /**
     * 判定的口径签名，用于「全局规则剔除已被单基金规则覆盖的基金」。
     *
     * <p>与旧口径一致：只看判定的形状（种类 + 指标 + 参数 + 关系），不看阈值——同类型的单基金规则
     * 视为已覆盖该基金，避免同一基金被两条同类规则重复提醒。回撤止盈没有条件，用参数参与签名。
     */
    public String signature() {
        return kind.name() + "|" + (conditions == null ? String.valueOf(takeProfit) : shape(conditions));
    }

    private static String shape(ConditionGroup group) {
        return group.conditions().stream()
                .map(condition -> condition.indicator().code() + condition.params() + condition.relation())
                .collect(Collectors.joining("+"));
    }

    private void apply(AlertRuleKind kind, ConditionGroup conditions, TakeProfitParams takeProfit) {
        AlertRuleKind validatedKind = Objects.requireNonNull(kind, "提醒规则种类不能为空");
        if (validatedKind.needsConditions()) {
            if (conditions == null) {
                throw new IllegalArgumentException("提醒条件不能为空");
            }
        } else if (conditions != null) {
            throw new IllegalArgumentException(validatedKind.label() + "的判定由参数决定，不配置条件");
        }
        if (validatedKind == AlertRuleKind.TRAILING_STOP) {
            if (takeProfit == null) {
                throw new IllegalArgumentException("回撤止盈必须配置止盈参数");
            }
        } else if (takeProfit != null) {
            throw new IllegalArgumentException(validatedKind.label() + "不接受止盈参数");
        }
        this.kind = validatedKind;
        this.conditions = conditions;
        this.takeProfit = takeProfit;
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

    private static long positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + "必须为正数");
        return value;
    }

    public Long id() { return id; }
    public Long version() { return version; }
    public long ownerId() { return ownerId; }
    public AlertRuleScope scope() { return scope; }
    public Long portfolioFundId() { return portfolioFundId; }
    public AlertRuleKind kind() { return kind; }
    public ConditionGroup conditions() { return conditions; }
    public TakeProfitParams takeProfit() { return takeProfit; }
    public boolean enabled() { return enabled; }
}