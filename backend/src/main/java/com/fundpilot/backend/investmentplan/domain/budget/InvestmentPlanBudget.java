package com.fundpilot.backend.investmentplan.domain.budget;

import java.math.BigDecimal;

/** 用户的可选月度定投预算，仅用于计划与现金流展示。 */
public final class InvestmentPlanBudget {
    private final Long id;
    private final long ownerId;
    private BigDecimal monthlyBudget;

    private InvestmentPlanBudget(Long id, long ownerId, BigDecimal monthlyBudget) {
        this.id = id;
        if (ownerId <= 0) throw new IllegalArgumentException("用户 ID 必须为正数");
        this.ownerId = ownerId;
        setMonthlyBudget(monthlyBudget);
    }
    public static InvestmentPlanBudget create(long ownerId, BigDecimal monthlyBudget) {
        return new InvestmentPlanBudget(null, ownerId, monthlyBudget);
    }
    public static InvestmentPlanBudget rehydrate(long id, long ownerId, BigDecimal monthlyBudget) {
        return new InvestmentPlanBudget(id, ownerId, monthlyBudget);
    }
    public void setMonthlyBudget(BigDecimal value) {
        if (value != null && (value.signum() <= 0 || value.scale() > 8
                || value.compareTo(new BigDecimal("99999999999.99999999")) > 0)) {
            throw new IllegalArgumentException("每月定投预算必须大于 0 且最多 8 位小数");
        }
        monthlyBudget = value;
    }
    public Long id() { return id; }
    public long ownerId() { return ownerId; }
    public BigDecimal monthlyBudget() { return monthlyBudget; }
}
