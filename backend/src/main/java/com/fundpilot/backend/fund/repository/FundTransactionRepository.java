package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.signal.enums.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundTransactionRepository extends JpaRepository<FundTransactionEntity, Long> {

    /**
     * 查所有指定状态的交易(issue #15 NavConfirmJob 遍历 PENDING 用)。
     */
    List<FundTransactionEntity> findByStatus(FundTransactionStatus status);

    /**
     * 按 fund_id + status 查交易行,供 {@code FundPositionService} 聚合持仓/在途份额。
     * 软删行由 {@code @SQLRestriction} 自动过滤。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndStatus(Long fundId, FundTransactionStatus status);

    /**
     * 按基金 + 信号类型 + 状态查交易(ADR-0015 后保留通用查询;SignalType 仅 NONE/SELL)。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndSignalLogEntity_SignalTypeAndStatus(
            Long fundId, SignalType signalType, FundTransactionStatus status);

    /**
     * 查某基金全部交易(归档级联逐个软删用,软删行由 @SQLRestriction 自动过滤)。
     */
    List<FundTransactionEntity> findByFundEntity_Id(Long fundId);

    /**
     * 查某基金全部交易按创建时间倒序(issue #18 交易流水 Tab 列表用)。
     * 软删行由 @SQLRestriction 自动过滤。
     */
    List<FundTransactionEntity> findByFundEntity_IdOrderByCreatedDateDesc(Long fundId);

    /**
     * 查某基金最新交易(issue #18 交易流水 Tab 列表用)。
     * 软删行由 @SQLRestriction 自动过滤。
     */
    FundTransactionEntity findTopByFundEntity_IdOrderByConfirmTimeDesc(Long fundId);
}
