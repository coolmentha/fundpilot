package com.fundpilot.backend.insights.infrastructure.persistence.portfolioreturn;

import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioReturnSnapshotRepositoryImpl implements PortfolioReturnSnapshotRepository {
    private final PortfolioReturnSnapshotJpaRepository snapshots;

    @Override public Optional<PortfolioReturnSnapshot> find(long ownerId, Instant businessDate) {
        return snapshots.findByOwnerIdAndBusinessDate(ownerId, businessDate).map(this::toDomain);
    }
    @Override public Optional<PortfolioReturnSnapshot> latestBefore(long ownerId, Instant businessDate) {
        return snapshots.findTopByOwnerIdAndBusinessDateBeforeOrderByBusinessDateDesc(ownerId, businessDate)
                .map(this::toDomain);
    }
    @Override public List<PortfolioReturnSnapshot> between(long ownerId, Instant from, Instant to) {
        return snapshots.findByOwnerIdAndBusinessDateBetweenOrderByBusinessDateAsc(ownerId, from, to).stream()
                .map(this::toDomain).toList();
    }
    @Override public PortfolioReturnSnapshot save(PortfolioReturnSnapshot value) {
        var row = value.id() == null ? new PortfolioReturnSnapshotJpaEntity()
                : snapshots.findById(value.id()).orElseThrow();
        Instant now = value.capturedAt();
        if (row.getId() == null) row.setCreatedDate(now);
        row.setUpdatedDate(now);
        row.setOwnerId(value.ownerId());
        row.setBusinessDate(value.businessDate());
        row.setInvestedAmount(value.investedAmount());
        row.setRedeemedAmount(value.redeemedAmount());
        row.setFeeAmount(value.feeAmount());
        row.setHoldingAmount(value.holdingAmount());
        row.setRealizedPnl(value.realizedPnl());
        row.setUnrealizedPnl(value.unrealizedPnl());
        row.setTotalReturn(value.totalReturn());
        row.setValuationComplete(value.valuationComplete());
        row.setMissingFundCodes(value.missingFundCodes());
        row.setCapturedAt(value.capturedAt());
        return toDomain(snapshots.save(row));
    }
    private PortfolioReturnSnapshot toDomain(PortfolioReturnSnapshotJpaEntity row) {
        return new PortfolioReturnSnapshot(row.getId(), row.getOwnerId(), row.getBusinessDate(),
                row.getInvestedAmount(), row.getRedeemedAmount(), row.getFeeAmount(), row.getHoldingAmount(),
                row.getRealizedPnl(), row.getUnrealizedPnl(), row.getTotalReturn(), row.isValuationComplete(),
                row.getMissingFundCodes(), row.getCapturedAt());
    }
}
