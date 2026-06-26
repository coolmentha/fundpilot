package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 交易视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 *
 * @param id                      交易 ID
 * @param fundId                  基金 ID
 * @param amount                  金额
 * @param shares                  份额
 * @param nav                     成交净值
 * @param status                  交易状态(PENDING/CONFIRMED/CANCELLED)
 * @param source                  交易来源(INCREASE/DECREASE/TRANSFER_IN/TRANSFER_OUT/INVEST)
 * @param confirmTime             确认时间
 * @param cancelTime              撤单时间
 * @param signalLogId             关联信号日志 ID
 * @param relatedTransactionId    关联交易 ID(转换交易互指)
 * @param createdDate             创建时间
 */
public record FundTransactionView(
        Long id,
        Long fundId,
        BigDecimal amount,
        BigDecimal shares,
        BigDecimal nav,
        FundTransactionStatus status,
        FundTransactionSource source,
        Instant confirmTime,
        Instant cancelTime,
        Long signalLogId,
        Long relatedTransactionId,
        Instant createdDate) {

    public static FundTransactionView from(FundTransactionEntity tx) {
        return new FundTransactionView(
                tx.getId(),
                tx.getFundEntity() != null ? tx.getFundEntity().getId() : null,
                tx.getAmount(),
                tx.getShares(),
                tx.getNav(),
                tx.getStatus(),
                tx.getSource(),
                tx.getConfirmTime(),
                tx.getCancelTime(),
                tx.getSignalLogEntity() != null ? tx.getSignalLogEntity().getId() : null,
                tx.getRelatedFundTransactionEntity() != null ? tx.getRelatedFundTransactionEntity().getId() : null,
                tx.getCreatedDate());
    }
}
