package com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundValidity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "portfolio_fund")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
class PortfolioFundJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @CreatedDate @Column(updatable = false) private Instant createdDate;
    @LastModifiedDate private Instant updatedDate;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "fund_product_id", nullable = false) private Long fundProductId;
    @Column(name = "legacy_fund_id") private Long legacyFundId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PortfolioFundValidity validity;
    @Column(name = "position_warning_enabled", nullable = false)
    private boolean positionWarningEnabled;
    @Column(name = "position_warning_ratio", nullable = false, precision = 19, scale = 8)
    private BigDecimal positionWarningRatio;
    @Column(name = "voided_at") private Instant voidedAt;
    @Column(name = "voided_by") private Long voidedBy;
    @Column(name = "void_reason", columnDefinition = "text") private String voidReason;
}
