package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易手动确认服务:PENDING → CONFIRMED,取该基金最新一期累计净值回填另一侧。
 * <p>与 {@link NavConfirmService} 的区别:NavConfirmJob 用"当天净值"批量确认今日 PENDING;
 * 本服务用"最新一期净值"手动确认单笔(净值已落库但交易仍 PENDING 的场景,如手动录入后立即确认)。
 * 转换交易(TRANSFER_OUT + TRANSFER_IN 互指 relatedTransaction)两条腿联动确认。
 *
 * <p>ADR-0013:INCREASE/TRANSFER_IN/INVEST 确认时加权更新 FundEntity.costPerShare,
 * 同一事务内执行——新单价 = (旧单价×旧份额 + 本次amount) / (旧份额+本次份额)。
 */
@Service
@RequiredArgsConstructor
public class TransactionConfirmService {

    private static final Logger log = LoggerFactory.getLogger(TransactionConfirmService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundTransactionRepository fundTransactionRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundRepository fundRepository;
    private final FundPositionService fundPositionService;

    /**
     * 手动确认一笔交易。PENDING→CONFIRMED,用最新净值回填另一侧;转换交易两条腿一起确认。
     *
     * @return 本次确认的交易列表(普通交易 1 条;转换 2 条)
     */
    @Transactional
    public List<FundTransactionEntity> confirm(Long transactionId) {
        FundTransactionEntity tx = fundTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND,
                        "FundTransaction #" + transactionId + " 不存在"));
        if (tx.getStatus() == FundTransactionStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ALREADY_CONFIRMED,
                    "已确认交易不可再确认 #" + transactionId);
        }
        if (tx.getStatus() == FundTransactionStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ALREADY_CANCELLED,
                    "已撤销交易不可确认 #" + transactionId);
        }

        List<FundTransactionEntity> confirmed = new ArrayList<>();
        confirmOne(tx, confirmed);

        // 转换交易:relatedTransaction 两条腿一起确认
        FundTransactionEntity related = tx.getRelatedFundTransactionEntity();
        if (related != null && related.getStatus() == FundTransactionStatus.PENDING) {
            confirmOne(related, confirmed);
        }

        log.info("手动确认完成 tx_id={} confirmed={}", transactionId, confirmed.size());
        return confirmed;
    }

    private void confirmOne(FundTransactionEntity tx, List<FundTransactionEntity> confirmed) {
        BigDecimal navValue = latestAccumulatedNav(tx.getFundEntity().getId());
        FundTransactionSource source = tx.getSource();
        switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                if (tx.getAmount() == null) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            "买入类确认需有 amount,tx_id=" + tx.getId());
                }
                tx.setShares(tx.getAmount().divide(navValue, MATH));
            }
            case DECREASE, TRANSFER_OUT -> {
                if (tx.getShares() == null) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            "卖出类确认需有 shares,tx_id=" + tx.getId());
                }
                tx.setAmount(tx.getShares().multiply(navValue, MATH));
            }
        }
        tx.setNav(navValue);
        tx.setConfirmTime(Instant.now());
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        fundTransactionRepository.save(tx);
        confirmed.add(tx);

        // ADR-0013:买入类交易确认后加权更新 costPerShare
        updateCostPerShare(tx, source);
    }

    /**
     * INCREASE/TRANSFER_IN/INVEST 确认后加权更新 FundEntity.costPerShare。
     * <p>公式:新单价 = (旧单价×确认前旧份额 + 本次amount) / (旧份额+本次份额)。
     * 卖出/DECREASE/TRANSFER_OUT 不触发。
     */
    private void updateCostPerShare(FundTransactionEntity tx, FundTransactionSource source) {
        if (source != FundTransactionSource.INCREASE
                && source != FundTransactionSource.TRANSFER_IN
                && source != FundTransactionSource.INVEST) {
            return; // 卖出类不改成本单价
        }
        Long fundId = tx.getFundEntity().getId();
        // 确认前的旧份额(不含本次交易——因为 getHoldingShares 查 CONFIRMED,本次刚保存)
        // 实际上 save 后 getHoldingShares 包含本次,需要减去本次份额得到旧份额
        BigDecimal totalAfter = fundPositionService.getHoldingShares(fundId);
        BigDecimal oldShares = totalAfter.subtract(tx.getShares());
        BigDecimal oldCostPerShare = tx.getFundEntity().getCostPerShare();

        BigDecimal newCostPerShare;
        if (oldCostPerShare == null || oldShares.signum() <= 0) {
            // 首笔买入或无旧成本:新单价 = amount / shares
            newCostPerShare = tx.getAmount().divide(tx.getShares(), MATH);
        } else {
            // 加权平均:(旧成本×旧份额 + 本次金额) / 总份额
            BigDecimal numerator = oldCostPerShare.multiply(oldShares).add(tx.getAmount());
            BigDecimal denominator = oldShares.add(tx.getShares());
            newCostPerShare = numerator.divide(denominator, MATH);
        }

        FundEntity fund = tx.getFundEntity();
        fund.setCostPerShare(newCostPerShare);
        fundRepository.save(fund);
        log.info("costPerShare 加权更新 fund={} oldShares={} oldCost={} newShares={} amount={} newCost={}",
                fundId, oldShares, oldCostPerShare, tx.getShares(), tx.getAmount(), newCostPerShare);
    }

    /** 取该基金最新一期累计净值(净值未落库抛 NAV_HISTORY_EMPTY)。 */
    private BigDecimal latestAccumulatedNav(Long fundId) {
        List<FundNavHistoryEntity> latestTwo = fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(fundId);
        if (latestTwo.isEmpty()) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY,
                    "基金 #" + fundId + " 无净值历史,请先拉取行情");
        }
        BigDecimal nav = latestTwo.get(0).getAccumulatedNav();
        if (nav == null || nav.signum() <= 0) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY,
                    "基金 #" + fundId + " 最新净值为空或非正,无法确认");
        }
        return nav;
    }
}
