package com.fundpilot.backend.investmentplan.domain.investmentplan;

import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 用户组合基金的定投规则；交易事实由 Accounting 独占。 */
public final class InvestmentPlan {
    private final Long id;
    private final Long legacyDcaPlanId;
    private final long portfolioFundId;
    private final long ownerId;
    private boolean enabled;
    private BigDecimal amount;
    private InvestmentPlanFrequency frequency;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private InvestmentPlanStatus status;
    private final Instant createdDate;

    private InvestmentPlan(Long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId, boolean enabled,
                           BigDecimal amount, InvestmentPlanFrequency frequency, Integer dayOfWeek,
                           Integer dayOfMonth, InvestmentPlanStatus status, Instant createdDate) {
        this.id = id;
        this.legacyDcaPlanId = legacyDcaPlanId;
        this.portfolioFundId = positive(portfolioFundId, "组合基金 ID");
        this.ownerId = positive(ownerId, "用户 ID");
        this.enabled = enabled;
        this.amount = requireAmount(amount);
        this.frequency = Objects.requireNonNull(frequency, "定投频率不能为空");
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.status = Objects.requireNonNull(status, "计划状态不能为空");
        this.createdDate = createdDate;
        validateSchedule();
    }

    public static InvestmentPlan rehydrate(long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId,
                                           boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                                           Integer dayOfWeek, Integer dayOfMonth, InvestmentPlanStatus status) {
        return rehydrate(id, legacyDcaPlanId, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, status, null);
    }

    public static InvestmentPlan rehydrate(long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId,
                                           boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                                           Integer dayOfWeek, Integer dayOfMonth, InvestmentPlanStatus status,
                                           Instant createdDate) {
        return new InvestmentPlan(positive(id, "计划 ID"), legacyDcaPlanId, portfolioFundId, ownerId, enabled,
                amount, frequency, dayOfWeek, dayOfMonth, status, createdDate);
    }

    public static InvestmentPlan create(long portfolioFundId, long ownerId, boolean enabled, BigDecimal amount,
                                        InvestmentPlanFrequency frequency, Integer dayOfWeek, Integer dayOfMonth) {
        return new InvestmentPlan(null, null, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, InvestmentPlanStatus.EFFECTIVE, null);
    }

    public void update(boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                       Integer dayOfWeek, Integer dayOfMonth) {
        this.enabled = enabled;
        this.amount = requireAmount(amount);
        this.frequency = Objects.requireNonNull(frequency, "定投频率不能为空");
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        validateSchedule();
    }

    public void activate() {
        status = InvestmentPlanStatus.EFFECTIVE;
    }

    public void retire() {
        if (status != InvestmentPlanStatus.EFFECTIVE) throw new IllegalStateException("计划未生效");
        status = InvestmentPlanStatus.DRAFT;
    }

    public void setEnabled(boolean enabled) {
        if (status != InvestmentPlanStatus.EFFECTIVE) throw new IllegalStateException("计划未生效");
        this.enabled = enabled;
    }

    /**
     * 该计划今日是否应执行。
     *
     * <p>月定投(issue #150)：补执行不依赖「前序交易日严格早于计划日」推断，而由调用方传入
     * 真实的「本自然月计划日是否已有交易」。计划日当天 Job 未跑时，次日起仍可安全补跑一次
     * (幂等由账目层的「同一计划同一交易日」检查兜底)，且该自然月内不重复执行。
     *
     * @param alreadyExecutedThisMonth 本自然月内该计划是否已有任意状态交易(仅 MONTHLY 使用)
     */
    public boolean executableOn(Instant instant, boolean alreadyExecutedThisMonth) {
        if (!enabled || status != InvestmentPlanStatus.EFFECTIVE) {
            return false;
        }
        var date = BusinessDay.toDateLabel(instant).atZone(BusinessDay.ZONE).toLocalDate();
        return switch (frequency) {
            case DAILY -> true;
            case WEEKLY -> date.getDayOfWeek().getValue() == dayOfWeek;
            case MONTHLY -> {
                Instant scheduledLabel = date.withDayOfMonth(dayOfMonth)
                        .atStartOfDay(BusinessDay.ZONE)
                        .withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
                yield !BusinessDay.toDateLabel(instant).isBefore(scheduledLabel)
                        && !alreadyExecutedThisMonth;
            }
        };
    }

    public void disableForVoidedPortfolioFund() {
        enabled = false;
        status = InvestmentPlanStatus.DRAFT;
    }

    private void validateSchedule() {
        switch (frequency) {
            case DAILY -> {
                dayOfWeek = null;
                dayOfMonth = null;
            }
            case WEEKLY -> {
                if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 5) {
                    throw new IllegalArgumentException("周定投日必须为周一至周五");
                }
                dayOfMonth = null;
            }
            case MONTHLY -> {
                if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28) {
                    throw new IllegalArgumentException("月定投日必须在 1 至 28 日之间");
                }
                dayOfWeek = null;
            }
        }
    }

    private static BigDecimal requireAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("每次定投金额必须大于 0");
        return value;
    }

    private static long positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + "必须为正数");
        return value;
    }

    public Long id() { return id; }
    public Long legacyDcaPlanId() { return legacyDcaPlanId; }
    public long portfolioFundId() { return portfolioFundId; }
    public long ownerId() { return ownerId; }
    public boolean enabled() { return enabled; }
    public BigDecimal amount() { return amount; }
    public InvestmentPlanFrequency frequency() { return frequency; }
    public Integer dayOfWeek() { return dayOfWeek; }
    public Integer dayOfMonth() { return dayOfMonth; }
    public InvestmentPlanStatus status() { return status; }
    public Instant createdDate() { return createdDate; }
}
