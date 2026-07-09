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
    private final TransactionConfirmSupport transactionConfirmSupport;

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
        FundTransactionEntity related = tx.getRelatedFundTransactionEntity();
        // 基金转换(task 07-08):(TRANSFER_OUT, TRANSFER_IN) 互指两腿,先确认转出得净金额,
        // 回填转入 amount,再确认转入算 shares/fee。用户从任一腿发起确认均按此顺序。
        if (isConversionPair(tx, related)) {
            FundTransactionEntity outLeg = tx.getSource() == FundTransactionSource.TRANSFER_OUT ? tx : related;
            FundTransactionEntity inLeg = outLeg == tx ? related : tx;
            confirmOne(outLeg, confirmed);
            if (inLeg.getStatus() == FundTransactionStatus.PENDING) {
                inLeg.setAmount(outLeg.getAmount());  // 转出净金额 = 转入本金
                confirmOne(inLeg, confirmed);
            }
        } else {
            confirmOne(tx, confirmed);
            // 非 conversion 的 relatedTransaction(预留场景):沿用级联确认
            if (related != null && related.getStatus() == FundTransactionStatus.PENDING) {
                confirmOne(related, confirmed);
            }
        }

        log.info("手动确认完成 tx_id={} confirmed={}", transactionId, confirmed.size());
        return confirmed;
    }

    /** 是否为基金转换互指对(TRANSFER_OUT <-> TRANSFER_IN)。 */
    private boolean isConversionPair(FundTransactionEntity a, FundTransactionEntity b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getSource() == FundTransactionSource.TRANSFER_OUT
                && b.getSource() == FundTransactionSource.TRANSFER_IN) {
            return true;
        }
        if (a.getSource() == FundTransactionSource.TRANSFER_IN
                && b.getSource() == FundTransactionSource.TRANSFER_OUT) {
            return true;
        }
        return false;
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
            }
            case DECREASE, TRANSFER_OUT -> {
                if (tx.getShares() == null) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            "卖出类确认需有 shares,tx_id=" + tx.getId());
                }
            }
            // ADJUST 录入即 CONFIRMED,不会触达确认流程;此处仅覆盖枚举以防 switch 漏分支
            case ADJUST_IN, ADJUST_OUT -> {
            }
        }
        tx.setNav(navValue);
        tx.setConfirmTime(Instant.now());
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        // 扣手续费 + 建/消耗 lot + 更新成本单价(统一走 TransactionConfirmSupport)
        switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> transactionConfirmSupport.onBuyConfirmed(tx, navValue);
            case DECREASE, TRANSFER_OUT -> transactionConfirmSupport.onSellConfirmed(tx, navValue);
            // ADJUST 不建 lot/不算费(录入即 CONFIRMED,不触达此处)
            case ADJUST_IN, ADJUST_OUT -> {
            }
        }
        fundTransactionRepository.save(tx);
        confirmed.add(tx);
    }

    // updateCostPerShare 已移至 TransactionConfirmSupport(统一扣费 + lot + 成本更新)

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
