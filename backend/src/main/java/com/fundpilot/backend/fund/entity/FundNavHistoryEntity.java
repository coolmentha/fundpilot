package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class FundNavHistoryEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    private Instant navDate;

    private BigDecimal nav;

    private BigDecimal accumulatedNav;

}
