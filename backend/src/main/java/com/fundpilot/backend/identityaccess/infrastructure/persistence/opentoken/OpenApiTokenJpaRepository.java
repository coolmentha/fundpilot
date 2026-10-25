package com.fundpilot.backend.identityaccess.infrastructure.persistence.opentoken;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpenApiTokenJpaRepository extends JpaRepository<OpenApiTokenJpaEntity, Long> {

    Optional<OpenApiTokenJpaEntity> findByOwnerId(Long ownerId);

    Optional<OpenApiTokenJpaEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from OpenApiTokenJpaEntity token where token.ownerId = :ownerId")
    Optional<OpenApiTokenJpaEntity> findActiveByOwnerForUpdate(@Param("ownerId") Long ownerId);
}
