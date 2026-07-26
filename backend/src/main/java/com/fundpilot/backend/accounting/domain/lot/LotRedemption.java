package com.fundpilot.backend.accounting.domain.lot;

import java.math.BigDecimal;

/**
 * 卖出消耗 lot 的明细。每笔卖出按 FIFO 拆成多行，记录持有天数与命中的赎回费率，
 * 供校验与前端展示赎回费构成。
 */
public record LotRedemption(Long id, long lotId, long sellTransactionId,
                            BigDecimal sharesConsumed, int holdingDays, BigDecimal redemptionRate) {

    public static LotRedemption record(long lotId, long sellTransactionId, BigDecimal sharesConsumed,
                                       long holdingDays, BigDecimal redemptionRate) {
        return new LotRedemption(null, lotId, sellTransactionId, sharesConsumed,
                Math.toIntExact(holdingDays), redemptionRate);
    }
}
