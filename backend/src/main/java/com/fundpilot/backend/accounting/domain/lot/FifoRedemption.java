package com.fundpilot.backend.accounting.domain.lot;

import com.fundpilot.backend.sharedkernel.BusinessDay;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 卖出与调减时对 lot 的 FIFO 分配规则。
 *
 * <p>卖出按买入先后逐个消耗 lot，每段按其持有天数命中赎回费率阶梯并累加赎回费。
 * 未被 lot 覆盖的部分来自历史调增产生的未跟踪份额，按零赎回费处理，由调用方校验其合法性。
 */
public final class FifoRedemption {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private FifoRedemption() {
    }

    /**
     * 按 FIFO 消耗 lot 并计算赎回费。
     *
     * @param openLots      剩余份额大于 0 的 lot，须按买入时间升序
     * @param sellTransactionId 卖出流水 ID
     * @param sellShares    卖出份额
     * @param sellTradeTime 卖出交易发生时间，用于算持有天数
     * @param navValue      成交单位净值
     * @param feeSchedule   费率快照
     */
    public static Allocation allocate(List<Lot> openLots, long sellTransactionId, BigDecimal sellShares,
                                      Instant sellTradeTime, BigDecimal navValue,
                                      FeeSchedule feeSchedule) {
        BigDecimal remaining = sellShares;
        BigDecimal totalFee = BigDecimal.ZERO;
        List<Lot> touchedLots = new ArrayList<>();
        List<LotRedemption> redemptions = new ArrayList<>();

        for (Lot lot : openLots) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal consumed = lot.consume(remaining);
            if (consumed.signum() <= 0) {
                continue;
            }
            long holdingDays = BusinessDay.daysBetween(lot.acquireDate(), sellTradeTime);
            BigDecimal rate = feeSchedule.redemptionRateFor(holdingDays);
            totalFee = totalFee.add(consumed.multiply(navValue, MATH).multiply(rate, MATH));

            touchedLots.add(lot);
            redemptions.add(LotRedemption.record(
                    requireLotId(lot), sellTransactionId, consumed, holdingDays, rate));
            remaining = remaining.subtract(consumed);
        }

        return new Allocation(touchedLots, redemptions, totalFee, remaining);
    }

    /** 调减按 FIFO 缩减 open lot，不产生赎回费与赎回明细。 */
    public static Allocation allocateAdjustment(List<Lot> openLots, BigDecimal shares) {
        BigDecimal remaining = shares;
        List<Lot> touchedLots = new ArrayList<>();
        for (Lot lot : openLots) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal consumed = lot.consume(remaining);
            if (consumed.signum() <= 0) {
                continue;
            }
            touchedLots.add(lot);
            remaining = remaining.subtract(consumed);
        }
        return new Allocation(touchedLots, List.of(), BigDecimal.ZERO, remaining);
    }

    private static long requireLotId(Lot lot) {
        if (lot.id() == null) {
            throw new IllegalStateException("未持久化的 lot 不能被消耗");
        }
        return lot.id();
    }

    /**
     * FIFO 分配结果。
     *
     * @param unmatchedShares 未被 lot 覆盖的份额，来自历史调增的未跟踪份额
     */
    public record Allocation(List<Lot> touchedLots, List<LotRedemption> redemptions,
                             BigDecimal totalFee, BigDecimal unmatchedShares) {
    }
}
