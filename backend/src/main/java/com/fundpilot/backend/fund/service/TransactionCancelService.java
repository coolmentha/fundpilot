package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易撤单服务(issue #15):PENDING → CANCELLED;CONFIRMED 不可撤;
 * 基金转换(TRANSFER_OUT + TRANSFER_IN 互指 relatedTransaction)撤单时两条腿一起 CANCELLED。
 */
@Service
@RequiredArgsConstructor
public class TransactionCancelService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCancelService.class);

    private final FundTransactionRepository fundTransactionRepository;

    /**
     * 撤销交易。PENDING→CANCELLED;CONFIRMED 抛 {@code TRANSACTION_ALREADY_CONFIRMED};
     * 带 relatedTransaction 的转换交易,两条腿一起撤。
     *
     * @return 本次撤销的交易列表(普通交易 1 条;转换 2 条)
     */
    @Transactional
    public List<FundTransactionEntity> cancel(Long transactionId) {
        FundTransactionEntity tx = fundTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "FundTransaction #" + transactionId + " 不存在"));
        if (tx.getStatus() == FundTransactionStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ALREADY_CONFIRMED,
                    "已确认交易不可撤销 #" + transactionId);
        }
        if (tx.getStatus() == FundTransactionStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ALREADY_CANCELLED,
                    "交易已撤销 #" + transactionId);
        }

        List<FundTransactionEntity> cancelled = new ArrayList<>();
        cancelOne(tx, cancelled);

        // 转换交易:relatedTransaction 两条腿一起撤
        FundTransactionEntity related = tx.getRelatedFundTransactionEntity();
        if (related != null && related.getStatus() == FundTransactionStatus.PENDING) {
            cancelOne(related, cancelled);
        }

        log.info("撤单完成 tx_id={} cancelled={}", transactionId, cancelled.size());
        return cancelled;
    }

    private void cancelOne(FundTransactionEntity tx, List<FundTransactionEntity> cancelled) {
        tx.setStatus(FundTransactionStatus.CANCELLED);
        tx.setCancelTime(Instant.now());
        fundTransactionRepository.save(tx);
        cancelled.add(tx);
    }
}
