package com.fundpilot.backend.accounting.domain.position;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 持仓聚合。Accounting 独占 {@code openedAt}、{@code costPerShare} 与 {@code status}；
 * 状态和建仓时间由 CONFIRMED 账本校准，成本按买入加权并允许用户修正当前基准。
 */
public final class Position {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final Long id;
    private final long version;
    private final long portfolioFundId;
    private final long ownerId;
    private PositionStatus status;
    private Instant openedAt;
    private BigDecimal costPerShare;

    private Position(Long id, long version, long portfolioFundId, long ownerId, PositionStatus status,
                     Instant openedAt, BigDecimal costPerShare) {
        this.id = id;
        this.version = version;
        this.portfolioFundId = requirePositive(portfolioFundId, "组合基金 ID");
        this.ownerId = requirePositive(ownerId, "用户 ID");
        this.status = Objects.requireNonNull(status, "持仓状态不能为空");
        this.openedAt = openedAt;
        this.costPerShare = costPerShare;
    }

    public static Position empty(long portfolioFundId, long ownerId) {
        return new Position(null, 0L, portfolioFundId, ownerId, PositionStatus.EMPTY, null, null);
    }

    public static Position rehydrate(long id, long version, long portfolioFundId, long ownerId,
                                     PositionStatus status, Instant openedAt,
                                     BigDecimal costPerShare) {
        return new Position(requirePositive(id, "持仓 ID"), version, portfolioFundId, ownerId, status,
                openedAt, costPerShare);
    }

    /**
     * 按重放结果校准持仓状态与建仓时间。
     *
     * @param hasConfirmedLedger 是否存在 CONFIRMED 交易
     * @param netShares          CONFIRMED 净份额
     * @param latestInflowAt     最近一笔正向 CONFIRMED 交易时间，用于清仓再入场重建建仓时间
     * @return 状态发生变化时返回状态迁移事实，未变化返回 empty
     */
    public Optional<PositionTransition> reconcile(boolean hasConfirmedLedger, BigDecimal netShares,
                                                  Instant latestInflowAt) {
        PositionStatus target = !hasConfirmedLedger ? PositionStatus.EMPTY
                : netShares.signum() > 0 ? PositionStatus.OPEN : PositionStatus.CLEARED;
        PositionStatus previous = status;
        if (target == PositionStatus.OPEN && previous != PositionStatus.OPEN && latestInflowAt != null) {
            openedAt = latestInflowAt;
        }
        if (previous == target) {
            return Optional.empty();
        }
        status = target;
        return Optional.of(new PositionTransition(portfolioFundId, ownerId, previous, target));
    }

    /**
     * 买入确认后加权更新成本单价（ADR-0013）。
     * <p>新单价 = (旧单价 × 有成本旧份额 + 本次实际投入金额) / (全部旧份额 + 本次份额)。卖出不改单价；
     * 清仓再入场时旧份额为零，自然覆盖为本次单价。
     * <p>{@code ADJUST_IN} 的零成本份额只稀释分母、不参与分子加权（工作台领域上下文中的账实修正零成本语义）。
     *
     * @param sharesAfter     本次确认后的 CONFIRMED 净份额
     * @param acquiredShares  本次买入份额
     * @param effectiveAmount 用户实际投入金额（含申购费）
     * @param untrackedShares 全部 CONFIRMED 账本中未被收费 lot 跟踪的零成本份额（可空）
     */
    public void applyPurchase(BigDecimal sharesAfter, BigDecimal acquiredShares,
                              BigDecimal effectiveAmount, BigDecimal untrackedShares) {
        BigDecimal previousShares = sharesAfter.subtract(acquiredShares);
        if (costPerShare == null || previousShares.signum() <= 0) {
            costPerShare = effectiveAmount.divide(
                    previousShares.signum() > 0 ? previousShares.add(acquiredShares) : acquiredShares, MATH);
            return;
        }
        BigDecimal untracked = untrackedShares == null ? BigDecimal.ZERO : untrackedShares;
        BigDecimal trackedPrevious = previousShares.subtract(untracked).max(BigDecimal.ZERO);
        BigDecimal numerator = costPerShare.multiply(trackedPrevious, MATH).add(effectiveAmount);
        costPerShare = numerator.divide(previousShares.add(acquiredShares), MATH);
    }

    /** 期初持仓按用户输入的成本单价直接建立，不做加权。 */
    public void applyExistingPosition(BigDecimal existingCostPerShare, Instant openedAtSnapshot) {
        costPerShare = existingCostPerShare;
        openedAt = openedAtSnapshot;
    }

    /** 按包含成本基准重置的确认账本回放当前成本。 */
    public void applyReplayedCostPerShare(BigDecimal replayedCostPerShare) {
        if (replayedCostPerShare == null || replayedCostPerShare.signum() <= 0) {
            throw new IllegalArgumentException("重放成本单价必须大于 0");
        }
        costPerShare = replayedCostPerShare;
    }

    /** 用户修正当前持仓成本；历史交易和 lot 成本不随之改写。 */
    public void correctCostPerShare(BigDecimal correctedCostPerShare) {
        if (status != PositionStatus.OPEN) {
            throw new IllegalStateException("只有当前持仓可以修正成本单价");
        }
        if (correctedCostPerShare == null || correctedCostPerShare.signum() <= 0) {
            throw new IllegalArgumentException("成本单价必须大于 0");
        }
        costPerShare = correctedCostPerShare;
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + "必须为正数");
        }
        return value;
    }

    public Long id() { return id; }
    /** 乐观锁版本，作为持仓事件的幂等键组成部分。 */
    public long version() { return version; }
    public long portfolioFundId() { return portfolioFundId; }
    public long ownerId() { return ownerId; }
    public PositionStatus status() { return status; }
    public Instant openedAt() { return openedAt; }
    public BigDecimal costPerShare() { return costPerShare; }
}
