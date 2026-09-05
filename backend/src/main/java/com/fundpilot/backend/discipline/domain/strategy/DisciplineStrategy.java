package com.fundpilot.backend.discipline.domain.strategy;

import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 组合基金的卖出纪律策略；运行期状态留在同一聚合。 */
public final class DisciplineStrategy {
    private final Long id;
    private final long portfolioFundId;
    private final long ownerId;
    private StrategyParamStatus status;
    private BigDecimal activation;
    private BigDecimal pullback;
    private BigDecimal harvest;
    private BigDecimal minimumHolding;
    private BigDecimal maxSingleSell;
    private Integer cooldownDays;
    private String presetCategory;
    private Integer presetVersion;
    private boolean customized;
    private TakeProfitPhase takeProfitPhase;
    private Instant cycleStartedAt;
    private BigDecimal cyclePeakNav;
    private Long triggeredAdviceId;
    private Instant cooldownStartedAt;

    private DisciplineStrategy(Long id, long portfolioFundId, long ownerId, StrategyParamStatus status,
                               BigDecimal activation, BigDecimal pullback, BigDecimal harvest,
                               BigDecimal minimumHolding, BigDecimal maxSingleSell, Integer cooldownDays,
                               String presetCategory, Integer presetVersion, boolean customized,
                               TakeProfitPhase takeProfitPhase, Instant cycleStartedAt, BigDecimal cyclePeakNav,
                               Long triggeredAdviceId, Instant cooldownStartedAt) {
        this.id = id;
        this.portfolioFundId = positive(portfolioFundId, "组合基金 ID");
        this.ownerId = positive(ownerId, "用户 ID");
        this.status = Objects.requireNonNull(status, "策略状态不能为空");
        apply(activation, pullback, harvest, minimumHolding, maxSingleSell, cooldownDays);
        this.presetCategory = presetCategory;
        this.presetVersion = presetVersion;
        this.customized = customized;
        this.takeProfitPhase = takeProfitPhase;
        this.cycleStartedAt = cycleStartedAt;
        this.cyclePeakNav = cyclePeakNav;
        this.triggeredAdviceId = triggeredAdviceId;
        this.cooldownStartedAt = cooldownStartedAt;
    }
    public static DisciplineStrategy create(long portfolioFundId, long ownerId, Input input) {
        validateRequestActivation(input.activation());
        validatePreset(input.presetCategory(), input.presetVersion());
        return new DisciplineStrategy(null, portfolioFundId, ownerId, StrategyParamStatus.PENDING_CALIBRATION,
                input.activation(), input.pullback(), input.harvest(), input.minimumHolding(), input.maxSingleSell(),
                input.cooldownDays(), input.presetCategory(), input.presetVersion(), input.customized(),
                null, null, null, null, null);
    }
    public static DisciplineStrategy rehydrate(long id, long portfolioFundId, long ownerId,
                                                String status,
                                                BigDecimal activation, BigDecimal pullback, BigDecimal harvest,
                                                BigDecimal minimumHolding, BigDecimal maxSingleSell, Integer cooldownDays,
                                                String presetCategory, Integer presetVersion, boolean customized,
                                                String takeProfitPhase, Instant cycleStartedAt, BigDecimal cyclePeakNav,
                                                Long triggeredAdviceId, Instant cooldownStartedAt) {
        return new DisciplineStrategy(positive(id, "策略 ID"), portfolioFundId, ownerId,
                StrategyParamStatus.valueOf(status), activation,
                pullback, harvest, minimumHolding, maxSingleSell, cooldownDays,
                presetCategory, presetVersion, customized,
                takeProfitPhase == null ? null : TakeProfitPhase.valueOf(takeProfitPhase),
                cycleStartedAt, cyclePeakNav, triggeredAdviceId, cooldownStartedAt);
    }
    public void update(Input input) {
        if (status == StrategyParamStatus.EFFECTIVE) throw new IllegalStateException("请先停用策略再编辑");
        validateRequestActivation(input.activation());
        validatePreset(input.presetCategory(), input.presetVersion());
        apply(input.activation(), input.pullback(), input.harvest(), input.minimumHolding(), input.maxSingleSell(), input.cooldownDays());
        presetCategory = input.presetCategory();
        presetVersion = input.presetVersion();
        customized = input.customized();
    }
    public void activate() {
        if (status == StrategyParamStatus.EFFECTIVE) return;
        status = StrategyParamStatus.EFFECTIVE;
        positionOpened();
    }
    public void positionOpened() {
        if (status != StrategyParamStatus.EFFECTIVE) return;
        takeProfitPhase = TakeProfitPhase.ACCUMULATING;
        cycleStartedAt = null;
        cyclePeakNav = null;
        triggeredAdviceId = null;
        cooldownStartedAt = null;
    }

    public boolean prepareTakeProfit(BigDecimal overallReturn, BigDecimal currentAccumulatedNav,
                                     Instant today, boolean cooldownFinished) {
        if (takeProfitPhase == null) {
            takeProfitPhase = TakeProfitPhase.ACCUMULATING;
        }
        if (takeProfitPhase == TakeProfitPhase.TRIGGERED) {
            return false;
        }
        if (takeProfitPhase == TakeProfitPhase.COOLDOWN) {
            if (!cooldownFinished) {
                return false;
            }
            cooldownStartedAt = null;
            triggeredAdviceId = null;
            if (overallReturn.compareTo(activation) >= 0) {
                arm(currentAccumulatedNav, today);
            } else {
                takeProfitPhase = TakeProfitPhase.ACCUMULATING;
                cycleStartedAt = null;
                cyclePeakNav = null;
            }
            return false;
        }
        if (takeProfitPhase == TakeProfitPhase.ACCUMULATING) {
            if (overallReturn.compareTo(activation) >= 0) {
                arm(currentAccumulatedNav, today);
            }
            return false;
        }
        if (cyclePeakNav == null) {
            arm(currentAccumulatedNav, today);
            return false;
        }
        if (currentAccumulatedNav.compareTo(cyclePeakNav) > 0) {
            cyclePeakNav = currentAccumulatedNav;
            return false;
        }
        return true;
    }

    public void markTriggered(long adviceId) {
        triggeredAdviceId = positive(adviceId, "建议 ID");
        takeProfitPhase = TakeProfitPhase.TRIGGERED;
    }

    /** 止盈卖出确认后进入冷静期；非 TRIGGERED 态幂等忽略。 */
    public void enterCooldown(Instant now) {
        if (takeProfitPhase != TakeProfitPhase.TRIGGERED) {
            return;
        }
        takeProfitPhase = TakeProfitPhase.COOLDOWN;
        cooldownStartedAt = Objects.requireNonNull(now, "冷静期开始时间不能为空");
        cycleStartedAt = null;
        cyclePeakNav = null;
        triggeredAdviceId = null;
    }

    public void supersedeTriggered() {
        if (takeProfitPhase != TakeProfitPhase.TRIGGERED) {
            return;
        }
        takeProfitPhase = TakeProfitPhase.ARMED;
        triggeredAdviceId = null;
        cycleStartedAt = null;
        cyclePeakNav = null;
    }

    private void arm(BigDecimal currentAccumulatedNav, Instant today) {
        takeProfitPhase = TakeProfitPhase.ARMED;
        cycleStartedAt = today;
        cyclePeakNav = currentAccumulatedNav;
        triggeredAdviceId = null;
    }
    public void retire() {
        if (status != StrategyParamStatus.EFFECTIVE) throw new IllegalStateException("策略未生效");
        status = StrategyParamStatus.PENDING_CALIBRATION;
        takeProfitPhase = null;
        cycleStartedAt = null;
        cyclePeakNav = null;
        triggeredAdviceId = null;
        cooldownStartedAt = null;
    }
    private void apply(BigDecimal activation, BigDecimal pullback, BigDecimal harvest, BigDecimal minimumHolding,
                       BigDecimal maxSingleSell, Integer cooldownDays) {
        ratio("止盈启动收益率", activation, false, true);
        ratio("高点回撤比例", pullback, false, false);
        ratio("浮盈收割比例", harvest, false, true);
        ratio("最低保留仓位", minimumHolding, true, false);
        ratio("单次最大卖出比例", maxSingleSell, false, true);
        if (cooldownDays == null || cooldownDays < 0 || cooldownDays > 250) throw new IllegalArgumentException("冷静期交易日必须在 0 到 250 之间");
        this.activation = activation; this.pullback = pullback; this.harvest = harvest;
        this.minimumHolding = minimumHolding; this.maxSingleSell = maxSingleSell; this.cooldownDays = cooldownDays;
    }
    private static void validateRequestActivation(BigDecimal activation) {
        ratio("止盈启动收益率", activation, false, false);
    }
    private static void validatePreset(String category, Integer version) {
        if (category != null) {
            try {
                DisciplineCategory.valueOf(category);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("预设基金类别不存在");
            }
        }
        if (version != null && version != 1) {
            throw new IllegalArgumentException("预设策略版本不存在");
        }
    }
    private static void ratio(String label, BigDecimal value, boolean zero, boolean one) {
        if (value == null || (zero ? value.signum() < 0 : value.signum() <= 0)
                || (one ? value.compareTo(BigDecimal.ONE) > 0 : value.compareTo(BigDecimal.ONE) >= 0))
            throw new IllegalArgumentException(label + "取值范围非法");
    }
    private static long positive(long value, String field) { if (value <= 0) throw new IllegalArgumentException(field + "必须为正数"); return value; }
    public Long id() { return id; } public long portfolioFundId() { return portfolioFundId; }
    public long ownerId() { return ownerId; }
    public StrategyParamStatus status() { return status; }
    public BigDecimal activation() { return activation; } public BigDecimal pullback() { return pullback; }
    public BigDecimal harvest() { return harvest; } public BigDecimal minimumHolding() { return minimumHolding; }
    public BigDecimal maxSingleSell() { return maxSingleSell; } public Integer cooldownDays() { return cooldownDays; }
    public String presetCategory() { return presetCategory; }
    public Integer presetVersion() { return presetVersion; }
    public boolean customized() { return customized; }
    public TakeProfitPhase takeProfitPhase() { return takeProfitPhase; }
    public Instant cycleStartedAt() { return cycleStartedAt; }
    public BigDecimal cyclePeakNav() { return cyclePeakNav; }
    public Long triggeredAdviceId() { return triggeredAdviceId; }
    public Instant cooldownStartedAt() { return cooldownStartedAt; }
    public record Input(BigDecimal activation, BigDecimal pullback, BigDecimal harvest, BigDecimal minimumHolding,
                        BigDecimal maxSingleSell, Integer cooldownDays, String presetCategory,
                        Integer presetVersion, boolean customized) {
        public Input(BigDecimal activation, BigDecimal pullback, BigDecimal harvest, BigDecimal minimumHolding,
                     BigDecimal maxSingleSell, Integer cooldownDays) {
            this(activation, pullback, harvest, minimumHolding, maxSingleSell, cooldownDays, null, null, true);
        }
    }
}
