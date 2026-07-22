package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import com.fundpilot.backend.strategy.service.TakeProfitLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final FundTransactionRepository fundTransactionRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final TransactionConfirmSupport transactionConfirmSupport;
    private final FundPositionService fundPositionService;
    private final TakeProfitLifecycleService takeProfitLifecycleService;
    private final FundAccessService fundAccessService;

    /**
     * 手动确认一笔交易。PENDING→CONFIRMED,用交易发生日净值回填另一侧;转换交易两条腿一起确认。
     *
     * @return 本次确认的交易列表(普通交易 1 条;转换 2 条)
     */
    @Transactional
    public List<FundTransactionEntity> confirm(Long transactionId) {
        FundTransactionEntity tx = fundTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND,
                        "FundTransaction #" + transactionId + " 不存在"));
        fundAccessService.requireOwned(tx.getFundEntity());
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
        ConversionTransactionPair conversion = ConversionTransactionPair.resolve(tx, related);
        if (conversion != null) {
            confirmConversion(conversion, confirmed);
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

    private void confirmConversion(ConversionTransactionPair conversion,
                                   List<FundTransactionEntity> confirmed) {
        FundTransactionEntity outLeg = conversion.outLeg();
        FundTransactionEntity inLeg = conversion.inLeg();
        if (outLeg.getStatus() == FundTransactionStatus.CANCELLED
                || inLeg.getStatus() == FundTransactionStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ALREADY_CANCELLED,
                    "基金转换存在已撤销关联腿,不可确认");
        }
        if (outLeg.getStatus() == FundTransactionStatus.PENDING
                && inLeg.getStatus() == FundTransactionStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "基金转换转入腿已确认但转出腿仍待确认");
        }
        if (outLeg.getStatus() == FundTransactionStatus.PENDING) {
            confirmOne(outLeg, confirmed);
        }
        if (inLeg.getStatus() == FundTransactionStatus.PENDING) {
            if (outLeg.getStatus() != FundTransactionStatus.CONFIRMED || outLeg.getAmount() == null) {
                throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                        "基金转换转出腿尚未完成,不可确认转入腿");
            }
            inLeg.setAmount(outLeg.getAmount());
            confirmOne(inLeg, confirmed);
        }
    }

    private void confirmOne(FundTransactionEntity tx, List<FundTransactionEntity> confirmed) {
        if (tx.getStatus() != FundTransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "仅 PENDING 交易可确认,tx_id=" + tx.getId());
        }
        BigDecimal navValue = transactionDayUnitNav(tx);
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
        // 扣手续费 + 建/消耗 lot + 更新成本单价(统一走 TransactionConfirmSupport)
        switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                tx.setStatus(FundTransactionStatus.CONFIRMED);
                transactionConfirmSupport.onBuyConfirmed(tx, navValue);
            }
            case DECREASE, TRANSFER_OUT -> {
                transactionConfirmSupport.onSellConfirmed(tx, navValue);
                tx.setStatus(FundTransactionStatus.CONFIRMED);
            }
            // ADJUST 不建 lot/不算费(录入即 CONFIRMED,不触达此处)
            case ADJUST_IN, ADJUST_OUT -> tx.setStatus(FundTransactionStatus.CONFIRMED);
        }
        fundTransactionRepository.save(tx);
        takeProfitLifecycleService.onTransactionConfirmed(tx);
        fundPositionService.reconcileStatus(tx.getFundEntity().getId());
        confirmed.add(tx);
    }

    // updateCostPerShare 已移至 TransactionConfirmSupport(统一扣费 + lot + 成本更新)

    /** 取交易发生日单位净值，禁止用次日或最新一期净值替代历史成交净值。 */
    private BigDecimal transactionDayUnitNav(FundTransactionEntity transaction) {
        Instant dayStart = TransactionTradeDate.resolve(transaction, Instant.now());
        List<FundNavHistoryEntity> rows = fundNavHistoryRepository
                .findByFundEntity_IdAndNavDateGreaterThanEqualAndNavDateLessThan(
                        transaction.getFundEntity().getId(), dayStart,
                        dayStart.plus(1, ChronoUnit.DAYS));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY,
                    "基金 #" + transaction.getFundEntity().getId() + " 缺少交易日 " + dayStart + " 的净值");
        }
        BigDecimal nav = rows.get(0).getNav();
        if (nav == null || nav.signum() <= 0) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY,
                    "基金 #" + transaction.getFundEntity().getId() + " 交易日净值为空或非正,无法确认");
        }
        return nav;
    }
}
