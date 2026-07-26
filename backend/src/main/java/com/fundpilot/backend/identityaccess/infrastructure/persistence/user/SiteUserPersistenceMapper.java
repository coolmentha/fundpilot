package com.fundpilot.backend.identityaccess.infrastructure.persistence.user;

import com.fundpilot.backend.identityaccess.domain.user.User;

final class SiteUserPersistenceMapper {

    private SiteUserPersistenceMapper() {
    }

    static User toDomain(SiteUserJpaEntity entity) {
        return User.rehydrate(entity.getId(), entity.getUsername(), entity.getPasswordHash(),
                entity.getRole(), entity.isEnabled());
    }

    static SiteUserJpaEntity toEntity(User user) {
        SiteUserJpaEntity entity = new SiteUserJpaEntity();
        entity.setId(user.id());
        entity.setUsername(user.username());
        entity.setPasswordHash(user.passwordHash());
        entity.setRole(user.role());
        entity.setEnabled(user.enabled());
        return entity;
    }

    static void copyMutable(User user, SiteUserJpaEntity entity) {
        entity.setRole(user.role());
        entity.setEnabled(user.enabled());
    }
}
