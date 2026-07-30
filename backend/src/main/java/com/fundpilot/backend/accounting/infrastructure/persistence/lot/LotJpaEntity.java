package com.fundpilot.backend.accounting.infrastructure.persistence.lot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/** 买入 lot 持久化实体。扩展期同时写 {@code portfolio_fund_id} 与 legacy {@code fund_id}。 */
@Entity
@Table(name = "fund_lot")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@SQLDelete(sql = "UPDATE fund_lot SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
class LotJpaEntity {

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

    @Column(name = "deleted_date", insertable = false, updatable = false)
    private Instant deletedDate;

    @Column(name = "portfolio_fund_id")
    private Long portfolioFundId;

    @Column(name = "fund_id", nullable = false)
    private Long legacyFundId;

    @Column(name = "acquire_tx_id", nullable = false)
    private Long acquireTransactionId;

    @Column(name = "acquire_date", nullable = false)
    private Instant acquireDate;

    @Column(name = "acquire_shares", nullable = false, precision = 19, scale = 2)
    private BigDecimal acquireShares;

    @Column(name = "remaining_shares", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingShares;

    @Column(name = "acquire_cost_per_share", nullable = false)
    private BigDecimal acquireCostPerShare;
}
