package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;

final class PublishedNavPersistenceMapper {
    private PublishedNavPersistenceMapper() {}

    static PublishedNav toDomain(PublishedNavJpaEntity entity) {
        return new PublishedNav(entity.getId(), null, entity.getFundProductId(),
                entity.getFundCode(), entity.getNavDate(), entity.getNav(),
                entity.getAccumulatedNav(), entity.getFirstSeenAt());
    }

    static PublishedNavJpaEntity toEntity(PublishedNav nav) {
        PublishedNavJpaEntity entity = new PublishedNavJpaEntity();
        entity.setId(nav.id());
        entity.setFundProductId(nav.fundProductId());
        entity.setFundCode(nav.fundCode());
        entity.setNavDate(nav.navDate());
        entity.setNav(nav.unitNav());
        entity.setAccumulatedNav(nav.accumulatedNav());
        entity.setFirstSeenAt(nav.firstSeenAt());
        return entity;
    }
}
