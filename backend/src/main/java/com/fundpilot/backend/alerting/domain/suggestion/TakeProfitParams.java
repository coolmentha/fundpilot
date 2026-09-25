package com.fundpilot.backend.alerting.domain.suggestion;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 回撤止盈的六个参数；取值校验与取值范围完全照搬旧纪律策略（D6 先照搬不改）。
 *
 * @param activation     止盈启动收益率，(0, 1]
 * @param pullback       高点回撤比例，(0, 1)
 * @param harvest        浮盈收割比例，(0, 1]
 * @param minimumHolding 最低保留仓位，[0, 1)
 * @param maxSingleSell  单次最大卖出比例，(0, 1]
 * @param cooldownDays   冷静期交易日，0~250
 */
public record TakeProfitParams(BigDecimal activation, BigDecimal pullback, BigDecimal harvest,
                               BigDecimal minimumHolding, BigDecimal maxSingleSell, int cooldownDays) {

    public static final int MAX_COOLDOWN_DAYS = 250;

    public TakeProfitParams {
        activation = ratio("止盈启动收益率", activation, false, true);
        pullback = ratio("高点回撤比例", pullback, false, false);
        harvest = ratio("浮盈收割比例", harvest, false, true);
        minimumHolding = ratio("最低保留仓位", minimumHolding, true, false);
        maxSingleSell = ratio("单次最大卖出比例", maxSingleSell, false, true);
        if (cooldownDays < 0 || cooldownDays > MAX_COOLDOWN_DAYS) {
            throw new IllegalArgumentException("冷静期交易日必须在 0 到 " + MAX_COOLDOWN_DAYS + " 之间");
        }
    }

    /**
     * 比例取值校验：{@code zero} 为真表示允许 0，{@code one} 为真表示允许 1。
     */
    private static BigDecimal ratio(String label, BigDecimal value, boolean zero, boolean one) {
        Objects.requireNonNull(value, label + "不能为空");
        boolean tooSmall = zero ? value.signum() < 0 : value.signum() <= 0;
        boolean tooLarge = one ? value.compareTo(BigDecimal.ONE) > 0 : value.compareTo(BigDecimal.ONE) >= 0;
        if (tooSmall || tooLarge) {
            throw new IllegalArgumentException(label + "取值范围非法");
        }
        return value;
    }
}