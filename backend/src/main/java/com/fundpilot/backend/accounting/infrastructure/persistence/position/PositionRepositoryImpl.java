package com.fundpilot.backend.accounting.infrastructure.persistence.position;

import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.position.PositionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class PositionRepositoryImpl implements PositionRepository {

    private final PositionJpaRepository positions;

    @Override
    public Position save(Position position) {
        PositionJpaEntity entity = positions.findByPortfolioFundId(position.portfolioFundId())
                .orElseGet(PositionJpaEntity::new);
        entity.setPortfolioFundId(position.portfolioFundId());
        entity.setOwnerId(position.ownerId());
        entity.setStatus(position.status().name());
        entity.setOpenedAt(position.openedAt());
        entity.setCostPerShare(position.costPerShare());
        return toDomain(positions.save(entity));
    }

    @Override
    public Optional<Position> findByPortfolioFund(long portfolioFundId) {
        return positions.findByPortfolioFundId(portfolioFundId).map(PositionRepositoryImpl::toDomain);
    }

    @Override
    public List<Position> findByPortfolioFundIds(Collection<Long> portfolioFundIds) {
        if (portfolioFundIds.isEmpty()) {
            return List.of();
        }
        return positions.findByPortfolioFundIdIn(portfolioFundIds).stream()
                .map(PositionRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Position> findByOwner(long ownerId) {
        return positions.findByOwnerId(ownerId).stream().map(PositionRepositoryImpl::toDomain).toList();
    }

    @Override
    public void removeByPortfolioFund(long portfolioFundId) {
        positions.findByPortfolioFundId(portfolioFundId).ifPresent(positions::delete);
    }

    private static Position toDomain(PositionJpaEntity entity) {
        return Position.rehydrate(entity.getId(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getPortfolioFundId(), entity.getOwnerId(),
                PositionStatus.valueOf(entity.getStatus()), entity.getOpenedAt(),
                entity.getCostPerShare());
    }
}
