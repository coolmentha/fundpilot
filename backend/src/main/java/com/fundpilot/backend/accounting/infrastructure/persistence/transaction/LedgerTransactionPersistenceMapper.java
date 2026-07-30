package com.fundpilot.backend.accounting.infrastructure.persistence.transaction;

import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;

/** 领域聚合与 JPA 实体的双向映射；ownerId 由调用方从 portfolio_fund 解析后传入。 */
final class LedgerTransactionPersistenceMapper {

    private LedgerTransactionPersistenceMapper() {
    }

    static LedgerTransaction toDomain(LedgerTransactionJpaEntity entity, long ownerId) {
        return LedgerTransaction.rehydrate(entity.getId(), entity.getPortfolioFundId(), ownerId,
                TransactionSource.valueOf(entity.getSource()),
                TransactionStatus.valueOf(entity.getStatus()),
                entity.getAmount(), entity.getShares(), entity.getNav(), entity.getFee(),
                entity.getFeeRate(), entity.getTradeDate(), entity.getConfirmTime(),
                entity.getCancelTime(), entity.getCreatedDate(), entity.getRelatedTransactionId(),
                entity.getSignalLogId(), entity.getDcaPlanId(), entity.getDisciplineAdviceId(),
                entity.getInvestmentPlanId());
    }

    /** 把聚合状态写回实体；身份列（组合基金、legacy fund、来源、幂等键）只在新建时设置。 */
    static void applyState(LedgerTransactionJpaEntity entity, LedgerTransaction transaction) {
        entity.setStatus(transaction.status().name());
        entity.setAmount(transaction.amount());
        entity.setShares(transaction.shares());
        entity.setNav(transaction.nav());
        entity.setFee(transaction.fee());
        entity.setFeeRate(transaction.feeRate());
        entity.setTradeDate(transaction.tradeDate());
        entity.setConfirmTime(transaction.confirmTime());
        entity.setCancelTime(transaction.cancelTime());
        entity.setRelatedTransactionId(transaction.relatedTransactionId());
    }

    static LedgerTransactionJpaEntity newEntity(LedgerTransaction transaction, long legacyFundId) {
        LedgerTransactionJpaEntity entity = new LedgerTransactionJpaEntity();
        entity.setPortfolioFundId(transaction.portfolioFundId());
        entity.setLegacyFundId(legacyFundId);
        entity.setSource(transaction.source().name());
        entity.setSignalLogId(transaction.signalLogId());
        entity.setDcaPlanId(transaction.dcaPlanId());
        entity.setDisciplineAdviceId(transaction.disciplineAdviceId());
        entity.setInvestmentPlanId(transaction.investmentPlanId());
        applyState(entity, transaction);
        return entity;
    }
}
