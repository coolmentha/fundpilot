package com.fundpilot.backend.insights.infrastructure.persistence.portfolioreturn;

import com.fundpilot.backend.platform.persistence.InstantDateConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity(name = "InsightsPortfolioReturnSnapshot")
@Table(name = "portfolio_return_snapshot")
@SQLRestriction("deleted_date IS NULL")
@Getter
@Setter
public class PortfolioReturnSnapshotJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @Column(updatable = false) private Instant createdDate;
    private Instant updatedDate;
    @Column(name = "deleted_date", insertable = false, updatable = false) private Instant deletedDate;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(nullable = false)
    @Convert(converter = InstantDateConverter.class) private Instant businessDate;
    @Column(nullable = false) private BigDecimal investedAmount;
    @Column(nullable = false) private BigDecimal redeemedAmount;
    @Column(nullable = false) private BigDecimal feeAmount;
    @Column(nullable = false) private BigDecimal holdingAmount;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    @Column(nullable = false) private BigDecimal totalReturn;
    @Column(nullable = false) private boolean valuationComplete;
    @Column(length = 512) private String missingFundCodes;
    @Column(nullable = false) private Instant capturedAt;
}
