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

/** 卖出消耗 lot 的明细实体；通过 lot 与卖出流水定位，无需自带组合基金列。 */
@Entity
@Table(name = "fund_lot_redemption")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@SQLDelete(sql = "UPDATE fund_lot_redemption SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
class LotRedemptionJpaEntity {

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

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(name = "sell_tx_id", nullable = false)
    private Long sellTransactionId;

    @Column(name = "shares_consumed", nullable = false, precision = 19, scale = 2)
    private BigDecimal sharesConsumed;

    @Column(name = "holding_days", nullable = false)
    private Integer holdingDays;

    @Column(name = "redemption_rate", nullable = false)
    private BigDecimal redemptionRate;
}
