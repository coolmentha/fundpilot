package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundStrategyActivationRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.repository.FundLotRedemptionRepository;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基金归档服务(issue #1 §归档):软删除基金 + 级联软删关联数据。
 * <p>PRD §归档:归档=软删除,与 FundStatus 正交,任意状态可归档,关联数据一起软删。
 * FundEntity 无 @OneToMany 反向关联,无法靠 JPA 级联,由本服务显式逐表软删。
 * <p>必须逐个 {@code delete(entity)} 才能触发各实体的 {@code @SQLDelete} 软删;
 * 批量 {@code deleteAllByXxx} 生成 HQL DELETE 会绕过 @SQLDelete 变成硬删。
 * 删除顺序:先关联表(避免外键约束),最后 fund 自身,均在同一事务内。
 */
@Service
@RequiredArgsConstructor
public class FundArchiveService {

    private final FundRepository fundRepository;
    private final FundStrategyRepository fundStrategyRepository;
    private final FundStrategyActivationRepository fundStrategyActivationRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final SignalLogRepository signalLogRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final MarketIndicatorSnapshotRepository marketIndicatorSnapshotRepository;
    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundLotRepository fundLotRepository;
    private final FundLotRedemptionRepository fundLotRedemptionRepository;

    /**
     * 归档基金:级联软删关联数据 + 软删自身。
     *
     * @param fundId 基金 ID
     * @throws BusinessException 基金不存在
     */
    @Transactional
    public void archive(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        if (fundTransactionRepository.existsByFundEntity_IdAndStatus(fundId, FundTransactionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.FUND_HAS_PENDING_TRANSACTIONS,
                    "基金存在待确认交易，请先确认或撤销后再归档");
        }
        var transactions = fundTransactionRepository.findByFundEntity_Id(fundId);
        var lots = fundLotRepository.findByFundEntity_Id(fundId);
        var lotIds = lots.stream().map(com.fundpilot.backend.fund.entity.FundLotEntity::getId).toList();
        var transactionIds = transactions.stream().map(com.fundpilot.backend.fund.entity.FundTransactionEntity::getId).toList();
        var redemptions = new java.util.LinkedHashMap<Long, com.fundpilot.backend.fund.entity.FundLotRedemptionEntity>();
        if (!lotIds.isEmpty()) {
            fundLotRedemptionRepository.findByLotIdIn(lotIds)
                    .forEach(row -> redemptions.put(row.getId(), row));
        }
        if (!transactionIds.isEmpty()) {
            fundLotRedemptionRepository.findBySellTxIdIn(transactionIds)
                    .forEach(row -> redemptions.put(row.getId(), row));
        }
        fundLotRedemptionRepository.deleteAll(redemptions.values());
        fundLotRepository.deleteAll(lots);
        fundDcaPlanRepository.deleteAll(fundDcaPlanRepository.findByFundEntity_Id(fundId));
        // 先级联软删关联表(避免外键约束),再删自身。逐个 delete 触发 @SQLDelete。
        fundStrategyActivationRepository.deleteAll(
                fundStrategyActivationRepository.findByFundEntity_Id(fundId));
        fundTransactionRepository.deleteAll(transactions);
        signalLogRepository.deleteAll(signalLogRepository.findByFundEntity_Id(fundId));
        fundNavHistoryRepository.deleteAll(fundNavHistoryRepository.findByFundEntity_Id(fundId));
        marketIndicatorSnapshotRepository.deleteAll(marketIndicatorSnapshotRepository.findByFundEntity_Id(fundId));
        fundStrategyRepository.deleteAll(fundStrategyRepository.findByFundEntity_Id(fundId));
        fundRepository.delete(fund);
    }
}
