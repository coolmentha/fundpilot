package com.fundpilot.backend.user.repository;

import com.fundpilot.backend.user.entity.SiteUserEntity;
import java.util.Optional;
import com.fundpilot.backend.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteUserRepository extends JpaRepository<SiteUserEntity, Long> {
    Optional<SiteUserEntity> findByUsername(String username);
    long countByRoleAndEnabledTrue(UserRole role);
}
