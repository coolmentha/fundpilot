package com.fundpilot.backend.productcatalog.infrastructure.persistence.fee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.PurchaseStatus;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.RefreshStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "fund_fee")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@Getter
@Setter
class FundFeeScheduleJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @CreatedDate @Column(updatable = false) private Instant createdDate;
    @LastModifiedDate private Instant updatedDate;
    @Column(name = "deleted_date", insertable = false, updatable = false) private Instant deletedDate;
    @Column(name = "fund_code", nullable = false) private String fundCode;
    private BigDecimal purchaseRate;
    private BigDecimal discountRate;
    private BigDecimal salesServiceFee;
    private BigDecimal managementFee;
    private BigDecimal custodyFee;
    @Enumerated(EnumType.STRING) private PurchaseStatus purchaseStatus;
    private BigDecimal purchaseLimit;
    private BigDecimal minimumPurchaseAmount;
    private String channelReferenceLabel;
    private String sourceName;
    @Column(length = 1000) private String sourceUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RefreshStatus refreshStatus;
    @Column(length = 2000) private String redemptionLadder;
    @Column(nullable = false) private Instant fetchedAt;
}
