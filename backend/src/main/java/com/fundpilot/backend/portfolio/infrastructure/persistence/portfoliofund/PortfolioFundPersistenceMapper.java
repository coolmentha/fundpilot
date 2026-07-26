package com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFund;

final class PortfolioFundPersistenceMapper {
    private PortfolioFundPersistenceMapper() {
    }

    static PortfolioFund toDomain(PortfolioFundJpaEntity entity) {
        return PortfolioFund.rehydrate(entity.getId(), entity.getLegacyFundId(), entity.getOwnerId(),
                entity.getFundProductId(), entity.getValidity(), entity.isPositionWarningEnabled(),
                entity.getPositionWarningRatio(), entity.getVoidedAt(), entity.getVoidedBy(),
                entity.getVoidReason());
    }

    static PortfolioFundJpaEntity toEntity(PortfolioFund portfolioFund) {
        PortfolioFundJpaEntity entity = new PortfolioFundJpaEntity();
        entity.setOwnerId(portfolioFund.ownerId());
        entity.setFundProductId(portfolioFund.fundProductId());
        entity.setLegacyFundId(portfolioFund.legacyFundId());
        copyMutable(portfolioFund, entity);
        return entity;
    }

    static void copyMutable(PortfolioFund portfolioFund, PortfolioFundJpaEntity entity) {
        entity.setValidity(portfolioFund.validity());
        entity.setPositionWarningEnabled(portfolioFund.positionWarningEnabled());
        entity.setPositionWarningRatio(portfolioFund.positionWarningRatio());
        entity.setVoidedAt(portfolioFund.voidedAt());
        entity.setVoidedBy(portfolioFund.voidedBy());
        entity.setVoidReason(portfolioFund.voidReason());
    }
}
