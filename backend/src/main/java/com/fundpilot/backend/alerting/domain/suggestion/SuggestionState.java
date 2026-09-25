package com.fundpilot.backend.alerting.domain.suggestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 建议型规则的运行期状态（按「规则 × 组合基金」一行）。
 *
 * <p>回撤止盈照搬旧纪律策略的状态机：累计 → 收益率达标即就位并记录周期峰值 → 净值创新高则抬升峰值 →
 * 回撤达标即可触发 → 触发后进入冷静期。逻辑破坏止损只借用「已触发」标记，实现连续命中期间只提醒一次。
 */
public final class SuggestionState {

    private final Long id;
    private final long alertRuleId;
    private final long ownerId;
    private final long portfolioFundId;
    private TakeProfitPhase phase;
    private Instant cycleStartedAt;
    private BigDecimal cyclePeakNav;
    private Instant cooldownStartedAt;

    private SuggestionState(Long id, long alertRuleId, long ownerId, long portfolioFundId, TakeProfitPhase phase,
                            Instant cycleStartedAt, BigDecimal cyclePeakNav, Instant cooldownStartedAt) {
        this.id = id;
        this.alertRuleId = positive(alertRuleId, "提醒规则 ID");
        this.ownerId = positive(ownerId, "用户 ID");
        this.portfolioFundId = positive(portfolioFundId, "组合基金 ID");
        this.phase = phase == null ? TakeProfitPhase.ACCUMULATING : phase;
        this.cycleStartedAt = cycleStartedAt;
        this.cyclePeakNav = cyclePeakNav;
        this.cooldownStartedAt = cooldownStartedAt;
    }

    public static SuggestionState create(long alertRuleId, long ownerId, long portfolioFundId) {
        return new SuggestionState(null, alertRuleId, ownerId, portfolioFundId, TakeProfitPhase.ACCUMULATING, null,
                null, null);
    }

    public static SuggestionState rehydrate(Long id, long alertRuleId, long ownerId, long portfolioFundId,
                                            TakeProfitPhase phase, Instant cycleStartedAt, BigDecimal cyclePeakNav,
                                            Instant cooldownStartedAt) {
        return new SuggestionState(id, alertRuleId, ownerId, portfolioFundId, phase, cycleStartedAt, cyclePeakNav,
                cooldownStartedAt);
    }

    /**
     * 推进回撤止盈状态并判断当前是否可触发（照搬旧口径）。
     *
     * @param overallReturn        当前总体收益率；无可用持仓数据时为 null，不推进状态
     * @param currentAccumulatedNav 当前累计净值
     * @param activation           止盈启动收益率
     * @param today                当前交易日
     * @param cooldownFinished     冷静期是否已走完
     * @return true 表示已就位、净值未创新高且可触发（是否真的提醒由调用方按回撤阈值与发送结果决定）
     */
    public boolean prepareTakeProfit(BigDecimal overallReturn, BigDecimal currentAccumulatedNav,
                                     BigDecimal activation, Instant today, boolean cooldownFinished) {
        if (overallReturn == null || currentAccumulatedNav == null) {
            return false;
        }
        if (phase == TakeProfitPhase.TRIGGERED) {
            return false;
        }
        if (phase == TakeProfitPhase.COOLDOWN) {
            if (!cooldownFinished) {
                return false;
            }
            cooldownStartedAt = null;
            if (overallReturn.compareTo(activation) >= 0) {
                arm(currentAccumulatedNav, today);
            } else {
                accumulate();
            }
            return false;
        }
        if (phase == TakeProfitPhase.ACCUMULATING) {
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

    /** 标记为已触发/已提醒；连续命中期间的状态由此标记抑制重复提醒。 */
    public void markTriggered() {
        if (phase == TakeProfitPhase.COOLDOWN) {
            return;
        }
        phase = TakeProfitPhase.TRIGGERED;
    }

    /**
     * 进入冷静期：触发后一段时间内不再提醒。
     *
     * <p>通知链路降级为纯通知后不再等待「卖出确认」，因此发送成功即进入冷静期；非
     * {@link TakeProfitPhase#TRIGGERED} 状态幂等忽略。
     */
    public void enterCooldown(Instant now) {
        if (phase != TakeProfitPhase.TRIGGERED) {
            return;
        }
        phase = TakeProfitPhase.COOLDOWN;
        cooldownStartedAt = Objects.requireNonNull(now, "冷静期开始时间不能为空");
        cycleStartedAt = null;
        cyclePeakNav = null;
    }

    /** 条件不再命中（或持仓已清空）时回到起点，允许下一次重新提醒。 */
    public void reset() {
        accumulate();
    }

    /** 从未被推进过：既没就位过也没提醒过，无需落库。 */
    public boolean pristine() {
        return phase == TakeProfitPhase.ACCUMULATING && cycleStartedAt == null && cyclePeakNav == null
                && cooldownStartedAt == null;
    }

    private void arm(BigDecimal currentAccumulatedNav, Instant today) {
        phase = TakeProfitPhase.ARMED;
        cycleStartedAt = today;
        cyclePeakNav = currentAccumulatedNav;
    }

    private void accumulate() {
        phase = TakeProfitPhase.ACCUMULATING;
        cycleStartedAt = null;
        cyclePeakNav = null;
        cooldownStartedAt = null;
    }

    private static long positive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + "必须为正数");
        }
        return value;
    }

    public Long id() {
        return id;
    }

    public long alertRuleId() {
        return alertRuleId;
    }

    public long ownerId() {
        return ownerId;
    }

    public long portfolioFundId() {
        return portfolioFundId;
    }

    public TakeProfitPhase phase() {
        return phase;
    }

    public Instant cycleStartedAt() {
        return cycleStartedAt;
    }

    public BigDecimal cyclePeakNav() {
        return cyclePeakNav;
    }

    public Instant cooldownStartedAt() {
        return cooldownStartedAt;
    }
}