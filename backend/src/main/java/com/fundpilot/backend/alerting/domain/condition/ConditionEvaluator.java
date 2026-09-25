package com.fundpilot.backend.alerting.domain.condition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 无状态条件求值器：纯函数，不持有历史状态、不依赖 Spring。
 *
 * <p>每天每条规则对每个基金求值一次，口径固定为「最近一个交易日相对前一个交易日」，因此事件型条件
 * （金叉/死叉、上穿/下穿）只会在跨界当日触发，不需要额外的去重状态。
 */
public final class ConditionEvaluator {

    private static final int LATEST_ONLY = 1;
    private static final int PREVIOUS_AND_LATEST = 2;

    private ConditionEvaluator() {
    }

    /** 求值该条件所需的最少取值个数：事件型与放大/缩小类需要前一个取值。 */
    public static int requiredValues(AlertCondition condition) {
        Objects.requireNonNull(condition, "条件不能为空");
        return condition.relation().needsPrevious() ? PREVIOUS_AND_LATEST : LATEST_ONLY;
    }

    /**
     * 判断条件是否满足。
     *
     * @param values 按时间升序排列的指标取值（最新在最后、不含 null）；长度不足即视为不满足
     */
    public static boolean satisfied(AlertCondition condition, List<BigDecimal> values) {
        Objects.requireNonNull(condition, "条件不能为空");
        Objects.requireNonNull(values, "指标取值序列不能为空");
        int size = values.size();
        if (size < requiredValues(condition)) {
            return false;
        }
        BigDecimal latest = values.get(size - 1);
        BigDecimal threshold = condition.effectiveThreshold();
        return switch (condition.relation()) {
            case ABOVE -> latest.compareTo(threshold) >= 0;
            case BELOW -> latest.compareTo(threshold) <= 0;
            case CROSS_ABOVE -> crossed(values, threshold, true);
            case CROSS_BELOW -> crossed(values, threshold, false);
            case INCREASING -> latest.compareTo(values.get(size - 2)) > 0;
            case DECREASING -> latest.compareTo(values.get(size - 2)) < 0;
        };
    }

    private static boolean crossed(List<BigDecimal> values, BigDecimal threshold, boolean upward) {
        BigDecimal previous = values.get(values.size() - 2);
        BigDecimal latest = values.get(values.size() - 1);
        return upward
                ? previous.compareTo(threshold) <= 0 && latest.compareTo(threshold) > 0
                : previous.compareTo(threshold) >= 0 && latest.compareTo(threshold) < 0;
    }
}