package com.fundpilot.backend.identityaccess.infrastructure.persistence.user;

import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SiteUserJpaRepository extends JpaRepository<SiteUserJpaEntity, Long> {

    Optional<SiteUserJpaEntity> findByUsername(String username);

    Optional<SiteUserJpaEntity> findFirstByRoleAndEnabledTrueOrderByIdAsc(UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select siteUser from SiteUserJpaEntity siteUser "
            + "where siteUser.role = :role and siteUser.enabled = true order by siteUser.id")
    List<SiteUserJpaEntity> lockEnabledByRole(@Param("role") UserRole role, Pageable pageable);

    long countByRoleAndEnabledTrue(UserRole role);
}
