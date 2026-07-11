package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.client.FundFeeSnapshot;
import com.fundpilot.backend.fund.client.RedemptionTier;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundLotEntity;
import com.fundpilot.backend.fund.entity.FundLotRedemptionEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.repository.FundLotRedemptionRepository;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易确认共享逻辑:统一 {@link NavConfirmService} 与 {@link TransactionConfirmService} 的扣费 + lot 管理 + 成本更新。
 * <p>买入({@code onBuyConfirmed}):扣申购费 → {@code shares = (amount − fee) / nav} → 建 lot → 加权更新 costPerShare。
 * <p>卖出({@code onSellConfirmed}):FIFO 遍历 lot → 按持有期查赎回费率阶梯 → 算赎回费 → {@code amount = shares × nav − fee} → 记 lot_redemption。
 * <p>费率缺失(fund_fee 无记录)降级为不扣费(fee=0),记 warn 不阻断交易确认。
 */
@Component
@RequiredArgsConstructor
public class TransactionConfirmSupport {

    private static final Logger log = LoggerFactory.getLogger(TransactionConfirmSupport.class);
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundFeeService fundFeeService;
    private final FundLotRepository fundLotRepository;
    private final FundLotRedemptionRepository fundLotRedemptionRepository;
    private final FundPositionService fundPositionService;
    private final FundRepository fundRepository;

    /**
     * 买入确认:扣申购费,算 shares,建 lot,加权更新 costPerShare。
     * <p>调用前需已设 tx.amount / tx.nav / tx.confirmTime;调用后 tx.shares / tx.fee / tx.feeRate 被填。
     *
     * @param tx       交易实体(PENDING→CONFIRMED,confirmTime 已设)
     * @param navValue 交易日单位净值
     */
    public void onBuyConfirmed(FundTransactionEntity tx, BigDecimal navValue) {
        Long fundId = tx.getFundEntity().getId();
        FundFeeSnapshot fee = fundFeeService.getFeeByFundId(fundId);
        BigDecimal discountRate = fee.discountRate() != null ? fee.discountRate() : BigDecimal.ZERO;

        BigDecimal feeAmount = tx.getAmount().multiply(discountRate, MATH);
        BigDecimal netAmount = tx.getAmount().subtract(feeAmount);
        BigDecimal shares = netAmount.divide(navValue, MATH);

        tx.setShares(shares);
        tx.setFee(feeAmount);
        tx.setFeeRate(discountRate.signum() > 0 ? discountRate : null);

        // 建 lot
        FundLotEntity lot = new FundLotEntity();
        lot.setFundEntity(tx.getFundEntity());
        lot.setAcquireTxId(tx.getId());
        lot.setAcquireDate(TransactionTradeDate.resolveInstant(tx, tx.getConfirmTime()));
        lot.setAcquireShares(shares);
        lot.setRemainingShares(shares);
        lot.setAcquireCostPerShare(tx.getAmount().divide(shares, MATH));
        fundLotRepository.save(lot);

        // 申购费属于用户实际投入成本，成本分子使用完整交易金额。
        updateCostPerShare(tx, tx.getAmount());
        log.info("买入确认扣费 fund={} amount={} fee={} rate={} netAmount={} shares={} nav={}",
                fundId, tx.getAmount(), feeAmount, discountRate, netAmount, shares, navValue);
    }

    /**
     * 初始持仓是历史仓位盘点，不重复计算申购费，只建立后续赎回 FIFO 所需的 lot。
     */
    public void onExistingPositionConfirmed(FundTransactionEntity tx, BigDecimal acquireCostPerShare) {
        FundLotEntity lot = new FundLotEntity();
        lot.setFundEntity(tx.getFundEntity());
        lot.setAcquireTxId(tx.getId());
        lot.setAcquireDate(TransactionTradeDate.resolveInstant(tx, tx.getConfirmTime()));
        lot.setAcquireShares(tx.getShares());
        lot.setRemainingShares(tx.getShares());
        lot.setAcquireCostPerShare(acquireCostPerShare);
        fundLotRepository.save(lot);
    }

    /**
     * 卖出确认:FIFO 消耗 lot,按持有期查赎回费率,算 amount = shares × nav − fee。
     * <p>调用前需已设 tx.shares / tx.nav / tx.confirmTime;调用后 tx.amount / tx.fee / tx.feeRate 被填。
     *
     * @param tx       交易实体(PENDING→CONFIRMED,confirmTime 已设)
     * @param navValue 交易日单位净值
     */
    public void onSellConfirmed(FundTransactionEntity tx, BigDecimal navValue) {
        Long fundId = tx.getFundEntity().getId();
        FundFeeSnapshot fee = fundFeeService.getFeeByFundId(fundId);
        List<RedemptionTier> ladder = fee.redemptionLadder();

        BigDecimal remaining = tx.getShares();
        BigDecimal totalFee = BigDecimal.ZERO;
        List<FundLotRedemptionEntity> redemptions = new ArrayList<>();
        List<FundLotEntity> touchedLots = new ArrayList<>();

        List<FundLotEntity> lots = fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(fundId);
        if (lots.isEmpty()) {
            // 无 lot 记录(历史数据未回填 / 测试场景):降级不扣赎回费,按原逻辑 amount = shares × nav。
            // V14 迁移已回填生产数据,此处仅兜底边缘情况,记 warn 不阻断交易。
            BigDecimal grossAmount = tx.getShares().multiply(navValue, MATH);
            tx.setAmount(grossAmount);
            tx.setFee(BigDecimal.ZERO);
            tx.setFeeRate(null);
            log.warn("卖出确认无 lot 记录 fund={} shares={},降级不扣赎回费", fundId, tx.getShares());
            return;
        }
        BigDecimal trackedShares = lots.stream()
                .map(FundLotEntity::getRemainingShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal untrackedShares = untrackedSharesBeforeSell(tx, trackedShares);

        for (FundLotEntity lot : lots) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal consume = lot.getRemainingShares().min(remaining);
            Instant sellTradeTime = TransactionTradeDate.resolveInstant(tx, tx.getConfirmTime());
            long holdingDays = ChronoUnit.DAYS.between(
                    lot.getAcquireDate().atZone(ZoneOffset.UTC).toLocalDate(),
                    sellTradeTime.atZone(ZoneOffset.UTC).toLocalDate());
            BigDecimal rate = lookupRedemptionRate(ladder, (int) holdingDays);
            totalFee = totalFee.add(consume.multiply(navValue, MATH).multiply(rate, MATH));

            lot.setRemainingShares(lot.getRemainingShares().subtract(consume));
            touchedLots.add(lot);

            FundLotRedemptionEntity redemption = new FundLotRedemptionEntity();
            redemption.setLotId(lot.getId());
            redemption.setSellTxId(tx.getId());
            redemption.setSharesConsumed(consume);
            redemption.setHoldingDays((int) holdingDays);
            redemption.setRedemptionRate(rate);
            redemptions.add(redemption);

            remaining = remaining.subtract(consume);
        }

        if (remaining.signum() > 0) {
            if (remaining.compareTo(untrackedShares) > 0) {
                throw ErrorCode.INSUFFICIENT_LOTS.toException(
                        "可用 lot 份额不足,无法卖出 " + tx.getShares() + " 份,缺 " + remaining + " 份");
            }
            log.warn("卖出包含未跟踪调整份额 fund={} shares={} untracked={},未跟踪部分按零赎回费处理",
                    fundId, tx.getShares(), remaining);
        }

        BigDecimal grossAmount = tx.getShares().multiply(navValue, MATH);
        tx.setAmount(grossAmount.subtract(totalFee));
        tx.setFee(totalFee);
        tx.setFeeRate(grossAmount.signum() > 0 ? totalFee.divide(grossAmount, MATH) : null);

        fundLotRepository.saveAll(touchedLots);
        fundLotRedemptionRepository.saveAll(redemptions);
        log.info("卖出确认FIFO fund={} shares={} nav={} gross={} fee={} net={} lots={}",
                fundId, tx.getShares(), navValue, grossAmount, totalFee, tx.getAmount(), redemptions.size());
    }

    /** 调整交易确认:ADJUST_OUT 按 FIFO 缩减 open lot,不产生赎回费和赎回明细。 */
    public void onAdjustConfirmed(FundTransactionEntity tx) {
        if (tx.getSource() != FundTransactionSource.ADJUST_OUT) {
            return;
        }
        Long fundId = tx.getFundEntity().getId();
        BigDecimal remaining = tx.getShares();
        List<FundLotEntity> touchedLots = new ArrayList<>();
        for (FundLotEntity lot : fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(fundId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal consume = lot.getRemainingShares().min(remaining);
            lot.setRemainingShares(lot.getRemainingShares().subtract(consume));
            touchedLots.add(lot);
            remaining = remaining.subtract(consume);
        }
        if (!touchedLots.isEmpty()) {
            fundLotRepository.saveAll(touchedLots);
        }
        if (remaining.signum() > 0) {
            log.warn("调减份额超过 open lot fund={} shares={} unmatched={}", fundId, tx.getShares(), remaining);
        }
    }

    private BigDecimal untrackedSharesBeforeSell(FundTransactionEntity tx, BigDecimal trackedShares) {
        BigDecimal holdingAfter = fundPositionService.getHoldingShares(tx.getFundEntity().getId());
        if (holdingAfter == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal holdingBefore = holdingAfter.add(tx.getShares());
        return holdingBefore.subtract(trackedShares).max(BigDecimal.ZERO);
    }

    /**
     * 加权更新 FundEntity.costPerShare(ADR-0013)。
     * <p>新单价 = (旧单价 × 旧份额 + 本次有效金额) / (旧份额 + 本次份额)。
     * 卖出不改单价(由调用方保证仅在买入时调)。
     *
     * @param tx          买入交易(shares 已填)
     * @param effectiveAmount 用户实际投入金额(含申购费)
     */
    private void updateCostPerShare(FundTransactionEntity tx, BigDecimal effectiveAmount) {
        Long fundId = tx.getFundEntity().getId();
        BigDecimal totalAfter = fundPositionService.getHoldingShares(fundId);
        BigDecimal oldShares = totalAfter.subtract(tx.getShares());
        BigDecimal oldCostPerShare = tx.getFundEntity().getCostPerShare();

        BigDecimal newCostPerShare;
        if (oldCostPerShare == null || oldShares.signum() <= 0) {
            newCostPerShare = effectiveAmount.divide(tx.getShares(), MATH);
        } else {
            BigDecimal numerator = oldCostPerShare.multiply(oldShares, MATH).add(effectiveAmount);
            BigDecimal denominator = oldShares.add(tx.getShares());
            newCostPerShare = numerator.divide(denominator, MATH);
        }

        FundEntity fund = tx.getFundEntity();
        fund.setCostPerShare(newCostPerShare);
        fundRepository.save(fund);
    }

    /**
     * 按持有天数查赎回费率阶梯:遍历阶梯,首个 maxDays==null(最后一档)或 holdingDays < maxDays 的 tier.rate 即返回。
     * 阶梯空(费率缺失)返 ZERO(降级不扣赎回费)。
     */
    static BigDecimal lookupRedemptionRate(List<RedemptionTier> ladder, int holdingDays) {
        for (RedemptionTier tier : ladder) {
            if (tier.maxDays() == null || holdingDays < tier.maxDays()) {
                return tier.rate();
            }
        }
        return BigDecimal.ZERO;
    }
}
