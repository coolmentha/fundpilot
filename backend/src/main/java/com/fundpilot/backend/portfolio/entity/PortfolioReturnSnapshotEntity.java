package com.fundpilot.backend.portfolio.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.common.InstantDateConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "portfolio_return_snapshot")
@SQLDelete(sql = "UPDATE portfolio_return_snapshot SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class PortfolioReturnSnapshotEntity extends AbstractEntity {

    @Column(nullable = false)
    @Convert(converter = InstantDateConverter.class)
    private Instant businessDate;
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
