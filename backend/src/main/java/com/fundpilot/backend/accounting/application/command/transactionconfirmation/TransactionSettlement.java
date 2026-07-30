package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.accounting.domain.lot.FifoRedemption;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.ShareScale;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

/**
 * 结算算式。把「净值 + 费率 + lot 状态」折算为流水的确认结果，是确认用例内部的纯计算步骤。
 *
 * <p>买入：扣申购费 → {@code shares = (amount − fee) / nav} → 建 lot；成本分子使用完整交易金额，
 * 因为申购费同样是用户实际投入。
 * <p>卖出：FIFO 消耗 lot → 按各段持有期查赎回费率阶梯 → {@code amount = shares × nav − fee}。
 */
final class TransactionSettlement {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private TransactionSettlement() {
    }

    static Purchase settleBuy(LedgerTransaction transaction, BigDecimal navValue, FeeSchedule fees) {
        BigDecimal subscriptionRate = fees.subscriptionRate();
        BigDecimal feeAmount = transaction.amount().multiply(subscriptionRate, MATH);
        BigDecimal netAmount = transaction.amount().subtract(feeAmount);
        BigDecimal shares = ShareScale.normalize(netAmount.divide(navValue, MATH));

        LedgerTransaction.Settlement settlement = new LedgerTransaction.Settlement(navValue,
                transaction.amount(), shares, feeAmount,
                subscriptionRate.signum() > 0 ? subscriptionRate : null);
        BigDecimal acquireCostPerShare = transaction.amount().divide(shares, MATH);
        return new Purchase(settlement, shares, acquireCostPerShare, transaction.amount());
    }

    static Sale settleSell(LedgerTransaction transaction, BigDecimal navValue, FeeSchedule fees,
                           List<Lot> openLots, Instant sellTradeTime) {
        FifoRedemption.Allocation allocation = FifoRedemption.allocate(openLots, transaction.id(),
                transaction.shares(), sellTradeTime, navValue, fees);

        BigDecimal grossAmount = transaction.shares().multiply(navValue, MATH);
        BigDecimal netAmount = grossAmount.subtract(allocation.totalFee());
        LedgerTransaction.Settlement settlement = new LedgerTransaction.Settlement(navValue, netAmount,
                transaction.shares(), allocation.totalFee(),
                grossAmount.signum() > 0 ? allocation.totalFee().divide(grossAmount, MATH) : null);
        return new Sale(settlement, allocation);
    }

    /**
     * @param acquireCostPerShare lot 成本单价，含申购费
     * @param effectiveAmount     用户实际投入金额，用于持仓成本加权
     */
    record Purchase(LedgerTransaction.Settlement settlement, BigDecimal shares,
                    BigDecimal acquireCostPerShare, BigDecimal effectiveAmount) {
    }

    record Sale(LedgerTransaction.Settlement settlement, FifoRedemption.Allocation allocation) {
    }
}
