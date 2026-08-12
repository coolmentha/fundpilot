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
    private InvestmentPlanAmountStrategy amountStrategy;
    private String referenceIndexCode;
    private Integer movingAverageDays;
    private InvestmentPlanStatus status;
    private final Instant createdDate;

    private InvestmentPlan(Long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId, boolean enabled,
                           BigDecimal amount, InvestmentPlanFrequency frequency, Integer dayOfWeek,
                           Integer dayOfMonth, InvestmentPlanAmountStrategy amountStrategy,
                           String referenceIndexCode, Integer movingAverageDays,
                           InvestmentPlanStatus status, Instant createdDate) {
        this.id = id;
        this.legacyDcaPlanId = legacyDcaPlanId;
        this.portfolioFundId = positive(portfolioFundId, "组合基金 ID");
        this.ownerId = positive(ownerId, "用户 ID");
        this.enabled = enabled;
        this.amount = requireAmount(amount);
        this.frequency = Objects.requireNonNull(frequency, "定投频率不能为空");
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.amountStrategy = Objects.requireNonNullElse(amountStrategy, InvestmentPlanAmountStrategy.FIXED);
        this.referenceIndexCode = normalizeIndexCode(referenceIndexCode);
        this.movingAverageDays = this.amountStrategy == InvestmentPlanAmountStrategy.MOVING_AVERAGE
                ? movingAverageDays == null ? Integer.valueOf(250) : movingAverageDays : null;
        this.status = Objects.requireNonNull(status, "计划状态不能为空");
        this.createdDate = createdDate;
        validateSchedule();
        validateAmountStrategy(this.amountStrategy, this.referenceIndexCode, this.movingAverageDays);
    }

    public static InvestmentPlan rehydrate(long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId,
                                           boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                                           Integer dayOfWeek, Integer dayOfMonth, InvestmentPlanStatus status) {
        return rehydrate(id, legacyDcaPlanId, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, InvestmentPlanAmountStrategy.FIXED, null, null, status, null);
    }

    public static InvestmentPlan rehydrate(long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId,
                                           boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                                           Integer dayOfWeek, Integer dayOfMonth, InvestmentPlanStatus status,
                                           Instant createdDate) {
        return rehydrate(id, legacyDcaPlanId, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, InvestmentPlanAmountStrategy.FIXED, null, null, status, createdDate);
    }

    public static InvestmentPlan rehydrate(long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId,
                                           boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                                           Integer dayOfWeek, Integer dayOfMonth,
                                           InvestmentPlanAmountStrategy amountStrategy, String referenceIndexCode,
                                           Integer movingAverageDays, InvestmentPlanStatus status) {
        return rehydrate(id, legacyDcaPlanId, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, amountStrategy, referenceIndexCode, movingAverageDays, status, null);
    }

    public static InvestmentPlan rehydrate(long id, Long legacyDcaPlanId, long portfolioFundId, long ownerId,
                                           boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                                           Integer dayOfWeek, Integer dayOfMonth,
                                           InvestmentPlanAmountStrategy amountStrategy, String referenceIndexCode,
                                           Integer movingAverageDays, InvestmentPlanStatus status, Instant createdDate) {
        return new InvestmentPlan(positive(id, "计划 ID"), legacyDcaPlanId, portfolioFundId, ownerId, enabled,
                amount, frequency, dayOfWeek, dayOfMonth, amountStrategy, referenceIndexCode, movingAverageDays,
                status, createdDate);
    }

    public static InvestmentPlan create(long portfolioFundId, long ownerId, boolean enabled, BigDecimal amount,
                                        InvestmentPlanFrequency frequency, Integer dayOfWeek, Integer dayOfMonth) {
        return new InvestmentPlan(null, null, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, InvestmentPlanAmountStrategy.FIXED, null, null,
                InvestmentPlanStatus.EFFECTIVE, null);
    }

    public static InvestmentPlan create(long portfolioFundId, long ownerId, boolean enabled, BigDecimal amount,
                                        InvestmentPlanFrequency frequency, Integer dayOfWeek, Integer dayOfMonth,
                                        InvestmentPlanAmountStrategy amountStrategy, String referenceIndexCode,
                                        Integer movingAverageDays) {
        return new InvestmentPlan(null, null, portfolioFundId, ownerId, enabled, amount, frequency,
                dayOfWeek, dayOfMonth, amountStrategy, referenceIndexCode, movingAverageDays,
                InvestmentPlanStatus.EFFECTIVE, null);
    }

    public void update(boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                       Integer dayOfWeek, Integer dayOfMonth) {
        update(enabled, amount, frequency, dayOfWeek, dayOfMonth, amountStrategy, referenceIndexCode,
                movingAverageDays);
    }

    public void update(boolean enabled, BigDecimal amount, InvestmentPlanFrequency frequency,
                       Integer dayOfWeek, Integer dayOfMonth, InvestmentPlanAmountStrategy amountStrategy,
                       String referenceIndexCode, Integer movingAverageDays) {
        BigDecimal validatedAmount = requireAmount(amount);
        InvestmentPlanFrequency validatedFrequency = Objects.requireNonNull(frequency, "定投频率不能为空");
        validateSchedule(validatedFrequency, dayOfWeek, dayOfMonth);
        InvestmentPlanAmountStrategy validatedStrategy = Objects.requireNonNullElse(amountStrategy,
                InvestmentPlanAmountStrategy.FIXED);
        String validatedIndexCode = normalizeIndexCode(referenceIndexCode);
        validateAmountStrategy(validatedStrategy, validatedIndexCode, movingAverageDays);
        this.enabled = enabled;
        this.amount = validatedAmount;
        this.frequency = validatedFrequency;
        this.dayOfWeek = validatedFrequency == InvestmentPlanFrequency.DAILY ? null : dayOfWeek;
        this.dayOfMonth = validatedFrequency == InvestmentPlanFrequency.MONTHLY ? dayOfMonth : null;
        this.amountStrategy = validatedStrategy;
        this.referenceIndexCode = validatedIndexCode;
        this.movingAverageDays = validatedStrategy == InvestmentPlanAmountStrategy.MOVING_AVERAGE
                ? movingAverageDays == null ? 250 : movingAverageDays : null;
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
        return executableOn(instant, alreadyExecutedThisMonth, null);
    }

    /**
     * 该计划今日是否应执行。
     *
     * <p>月定投(issue #150/#158)：本月计划日已到或已过时按「是否已执行」判定；今日早于本月
     * 计划日时，若上一月计划日(issue #158 跨月顺延：月末连续休市)之后到昨天均非交易日，则
     * 今日补执行上一月计划日(由 {@code latestTradingDayBefore} 判定)。
     *
     * <p>新建计划(issue #159)：创建月内创建日已过计划日时，首笔执行顺延到下月计划日，避免
     * 创建当天意外买入。
     *
     * @param alreadyExecutedThisMonth  本自然月内该计划是否已有任意状态交易(仅 MONTHLY 使用)
     * @param latestTradingDayBefore    today 之前最近的交易日(仅 MONTHLY 跨月补跑使用；可空)
     */
    public boolean executableOn(Instant instant, boolean alreadyExecutedThisMonth, Instant latestTradingDayBefore) {
        if (!enabled || status != InvestmentPlanStatus.EFFECTIVE) {
            return false;
        }
        var date = BusinessDay.toDateLabel(instant).atZone(BusinessDay.ZONE).toLocalDate();
        return switch (frequency) {
            case DAILY -> true;
            case WEEKLY -> date.getDayOfWeek().getValue() == dayOfWeek;
            case MONTHLY -> {
                if (createdAfterPlanDayThisMonth(date)) {
                    yield false;
                }
                Instant scheduledLabel = date.withDayOfMonth(dayOfMonth)
                        .atStartOfDay(BusinessDay.ZONE)
                        .withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
                if (!BusinessDay.toDateLabel(instant).isBefore(scheduledLabel)) {
                    yield !alreadyExecutedThisMonth;
                }
                Instant previousMonthPlanLabel = date.minusMonths(1).withDayOfMonth(dayOfMonth)
                        .atStartOfDay(BusinessDay.ZONE)
                        .withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
                boolean existedBeforePreviousPlanDay = createdDate == null
                        || !BusinessDay.toDateLabel(createdDate).isAfter(previousMonthPlanLabel);
                yield existedBeforePreviousPlanDay
                        && latestTradingDayBefore != null
                        && latestTradingDayBefore.isBefore(previousMonthPlanLabel)
                        && !alreadyExecutedThisMonth;
            }
        };
    }

    /** 创建月内创建日已到或已过计划日时，本月不再执行(首笔顺延到下月)，避免创建当天意外买入(issue #159)。 */
    private boolean createdAfterPlanDayThisMonth(java.time.LocalDate today) {
        if (createdDate == null) {
            return false;
        }
        var created = BusinessDay.toDateLabel(createdDate).atZone(BusinessDay.ZONE).toLocalDate();
        return created.getYear() == today.getYear()
                && created.getMonthValue() == today.getMonthValue()
                && created.getDayOfMonth() >= dayOfMonth;
    }

    public void disableForVoidedPortfolioFund() {
        enabled = false;
        status = InvestmentPlanStatus.DRAFT;
    }

    private void validateSchedule() {
        validateSchedule(frequency, dayOfWeek, dayOfMonth);
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

    private static void validateSchedule(InvestmentPlanFrequency frequency, Integer dayOfWeek,
                                          Integer dayOfMonth) {
        switch (frequency) {
            case DAILY -> {
                // Daily plans do not require a day selector.
            }
            case WEEKLY -> {
                if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 5) {
                    throw new IllegalArgumentException("周定投日必须为周一至周五");
                }
            }
            case MONTHLY -> {
                if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28) {
                    throw new IllegalArgumentException("月定投日必须在 1 至 28 日之间");
                }
            }
        }
    }

    private static BigDecimal requireAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("每次定投金额必须大于 0");
        return value;
    }

    private static String normalizeIndexCode(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateAmountStrategy(InvestmentPlanAmountStrategy strategy, String indexCode,
                                                Integer movingAverageDays) {
        if (strategy == InvestmentPlanAmountStrategy.MOVING_AVERAGE
                && (indexCode == null || indexCode.isBlank())) {
            throw new IllegalArgumentException("均线策略必须选择参考指数");
        }
        if (strategy == InvestmentPlanAmountStrategy.MOVING_AVERAGE
                && movingAverageDays != null && movingAverageDays != 180
                && movingAverageDays != 250 && movingAverageDays != 500) {
            throw new IllegalArgumentException("均线周期只能为 180、250 或 500 日");
        }
        if (strategy != InvestmentPlanAmountStrategy.MOVING_AVERAGE && movingAverageDays != null) {
            throw new IllegalArgumentException("只有均线策略支持均线周期");
        }
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
    public InvestmentPlanAmountStrategy amountStrategy() { return amountStrategy; }
    public String referenceIndexCode() { return referenceIndexCode; }
    public Integer movingAverageDays() { return movingAverageDays; }
    public InvestmentPlanStatus status() { return status; }
    public Instant createdDate() { return createdDate; }
}
