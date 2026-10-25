package com.fundpilot.backend.identityaccess.infrastructure.persistence.opentoken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "open_api_token")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("revoked_at IS NULL")
@Getter
@Setter
public class OpenApiTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @CreatedDate
    @Column(updatable = false)
    private Instant createdDate;
    @LastModifiedDate
    private Instant updatedDate;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
