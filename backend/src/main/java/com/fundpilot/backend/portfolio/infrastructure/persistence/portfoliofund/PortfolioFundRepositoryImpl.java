package com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFund;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundRepository;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundValidity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.List;
import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
class PortfolioFundRepositoryImpl implements PortfolioFundRepository {
    private final PortfolioFundJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<PortfolioFund> findById(long id) {
        return repository.findById(id).map(PortfolioFundPersistenceMapper::toDomain);
    }

    @Override
    public Optional<PortfolioFund> findByIdForUpdate(long id) {
        return repository.findByIdForUpdate(id).map(PortfolioFundPersistenceMapper::toDomain);
    }

    @Override
    public Optional<PortfolioFund> findTrackedByOwnerIdAndFundProductId(long ownerId,
                                                                        long fundProductId) {
        return repository.findByOwnerIdAndFundProductIdAndValidity(
                        ownerId, fundProductId, PortfolioFundValidity.TRACKED)
                .map(PortfolioFundPersistenceMapper::toDomain);
    }

    @Override
    public Optional<PortfolioFund> findByLegacyFundId(long legacyFundId) {
        return repository.findByLegacyFundId(legacyFundId)
                .map(PortfolioFundPersistenceMapper::toDomain);
    }

    @Override
    public List<PortfolioFund> findByOwnerId(long ownerId) {
        return repository.findByOwnerIdOrderById(ownerId).stream()
                .map(PortfolioFundPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public PortfolioFund save(PortfolioFund portfolioFund) {
        PortfolioFundJpaEntity entity = portfolioFund.id() == null
                ? PortfolioFundPersistenceMapper.toEntity(portfolioFund)
                : repository.findById(portfolioFund.id()).orElseThrow();
        if (entity.getLegacyFundId() == null) {
            entity.setLegacyFundId(createLegacyBridge(portfolioFund.ownerId(), portfolioFund.fundProductId()));
        }
        PortfolioFundPersistenceMapper.copyMutable(portfolioFund, entity);
        PortfolioFund saved = PortfolioFundPersistenceMapper.toDomain(repository.save(entity));
        if (saved.validity() == PortfolioFundValidity.VOIDED && saved.legacyFundId() != null) {
            jdbcTemplate.update("""
                    UPDATE fund
                    SET deleted_date = COALESCE(deleted_date, ?), updated_date = now()
                    WHERE id = ?
                    """, Timestamp.from(saved.voidedAt()), saved.legacyFundId());
        }
        return saved;
    }

    @Override
    public List<PortfolioFund> findAllTracked() {
        return repository.findByValidityOrderById(PortfolioFundValidity.TRACKED).stream()
                .map(PortfolioFundPersistenceMapper::toDomain)
                .toList();
    }

    private long createLegacyBridge(long ownerId, long fundProductId) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO fund (version, created_date, updated_date, owner_id, product_id,
                                  fund_code, fund_name, status, position_warning_enabled,
                                  position_warning_ratio)
                SELECT 0, now(), now(), ?, id, fund_code, fund_name, 'PENDING_HOLDING', true, 0.30
                FROM fund_product WHERE id = ? AND deleted_date IS NULL
                RETURNING id
                """, Long.class, ownerId, fundProductId);
        if (id == null) throw new IllegalStateException("无法创建 legacy fund bridge: " + fundProductId);
        return id;
    }
}
