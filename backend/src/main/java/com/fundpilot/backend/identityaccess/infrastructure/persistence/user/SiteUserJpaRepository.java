package com.fundpilot.backend.identityaccess.infrastructure.persistence.user;

import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SiteUserJpaRepository extends JpaRepository<SiteUserJpaEntity, Long> {

    Optional<SiteUserJpaEntity> findByUsername(String username);

    Optional<SiteUserJpaEntity> findFirstByRoleAndEnabledTrueOrderByIdAsc(UserRole role);

    long countByRoleAndEnabledTrue(UserRole role);
}
