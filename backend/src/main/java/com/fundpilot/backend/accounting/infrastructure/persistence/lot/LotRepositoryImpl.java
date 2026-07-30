package com.fundpilot.backend.accounting.infrastructure.persistence.lot;

import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRedemption;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
class LotRepositoryImpl implements LotRepository {

    private final LotJpaRepository lots;
    private final LotRedemptionJpaRepository redemptions;
    private final JdbcTemplate jdbc;

    @Override
    public Lot save(Lot lot) {
        return toDomain(lots.save(toEntity(lot)));
    }

    @Override
    public void saveAll(List<Lot> updated) {
        if (updated.isEmpty()) {
            return;
        }
        lots.saveAll(updated.stream().map(this::toEntity).toList());
    }

    @Override
    public List<Lot> findOpenLotsOrderByAcquireDate(long portfolioFundId) {
        return lots.findOpenLotsOrderByAcquireDate(portfolioFundId).stream()
                .map(LotRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Lot> findByPortfolioFund(long portfolioFundId) {
        return lots.findByPortfolioFundIdOrderByAcquireDateAsc(portfolioFundId).stream()
                .map(LotRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Lot> findByPortfolioFundIds(Collection<Long> portfolioFundIds) {
        if (portfolioFundIds.isEmpty()) {
            return List.of();
        }
        return lots.findByPortfolioFundIdIn(portfolioFundIds).stream().map(LotRepositoryImpl::toDomain).toList();
    }

    @Override
    public void saveRedemptions(List<LotRedemption> newRedemptions) {
        if (newRedemptions.isEmpty()) {
            return;
        }
        redemptions.saveAll(newRedemptions.stream().map(redemption -> {
            LotRedemptionJpaEntity entity = new LotRedemptionJpaEntity();
            entity.setLotId(redemption.lotId());
            entity.setSellTransactionId(redemption.sellTransactionId());
            entity.setSharesConsumed(redemption.sharesConsumed());
            entity.setHoldingDays(redemption.holdingDays());
            entity.setRedemptionRate(redemption.redemptionRate());
            return entity;
        }).toList());
    }

    @Override
    public List<LotRedemption> findRedemptionsBySellTransactionIds(Collection<Long> sellTransactionIds) {
        if (sellTransactionIds.isEmpty()) {
            return List.of();
        }
        return redemptions.findBySellTransactionIdIn(sellTransactionIds).stream()
                .map(LotRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<LotRedemption> findRedemptionsByLotIds(Collection<Long> lotIds) {
        if (lotIds.isEmpty()) {
            return List.of();
        }
        return redemptions.findByLotIdIn(lotIds).stream().map(LotRepositoryImpl::toDomain).toList();
    }

    private LotJpaEntity toEntity(Lot lot) {
        LotJpaEntity entity = lot.id() == null ? new LotJpaEntity()
                : lots.findById(lot.id()).orElseThrow(() ->
                        new IllegalStateException("lot 不存在: " + lot.id()));
        if (lot.id() == null) {
            entity.setPortfolioFundId(lot.portfolioFundId());
            entity.setLegacyFundId(legacyFundId(lot.portfolioFundId()));
            entity.setAcquireTransactionId(lot.acquireTransactionId());
            entity.setAcquireDate(lot.acquireDate());
            entity.setAcquireShares(lot.acquireShares());
            entity.setAcquireCostPerShare(lot.acquireCostPerShare());
        }
        entity.setRemainingShares(lot.remainingShares());
        return entity;
    }

    private static Lot toDomain(LotJpaEntity entity) {
        return Lot.rehydrate(entity.getId(), entity.getPortfolioFundId(),
                entity.getAcquireTransactionId(), entity.getAcquireDate(), entity.getAcquireShares(),
                entity.getRemainingShares(), entity.getAcquireCostPerShare());
    }

    private static LotRedemption toDomain(LotRedemptionJpaEntity entity) {
        return new LotRedemption(entity.getId(), entity.getLotId(), entity.getSellTransactionId(),
                entity.getSharesConsumed(), entity.getHoldingDays(), entity.getRedemptionRate());
    }

    private long legacyFundId(long portfolioFundId) {
        Long legacyFundId = jdbc.queryForObject(
                "SELECT legacy_fund_id FROM portfolio_fund WHERE id = ?", Long.class, portfolioFundId);
        if (legacyFundId == null) {
            throw new IllegalStateException(
                    "组合基金 " + portfolioFundId + " 缺少 legacy fund 映射，扩展期不能写入 lot");
        }
        return legacyFundId;
    }
}
