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

    public boolean executableOn(Instant instant, Instant previousTradingDay) {
        if (!enabled || status != InvestmentPlanStatus.EFFECTIVE) {
            return false;
        }
        var date = BusinessDay.toDateLabel(instant).atZone(BusinessDay.ZONE).toLocalDate();
        return switch (frequency) {
            case DAILY -> true;
            case WEEKLY -> date.getDayOfWeek().getValue() == dayOfWeek;
            case MONTHLY -> {
                var scheduled = date.getDayOfMonth() >= dayOfMonth
                        ? date.withDayOfMonth(dayOfMonth)
                        : date.minusMonths(1).withDayOfMonth(dayOfMonth);
                Instant scheduledLabel = scheduled.atStartOfDay(BusinessDay.ZONE)
                        .withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
                yield !BusinessDay.toDateLabel(instant).isBefore(scheduledLabel)
                        && (previousTradingDay == null
                        || BusinessDay.toDateLabel(previousTradingDay).isBefore(scheduledLabel));
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
