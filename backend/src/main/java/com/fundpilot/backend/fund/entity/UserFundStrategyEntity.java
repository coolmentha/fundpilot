package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class UserFundStrategyEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StrategyParamStatus status;

    private BigDecimal tier1Drawdown;

    private BigDecimal tier2Drawdown;

    private BigDecimal tier3Drawdown;

    private BigDecimal tier4Drawdown;

    private BigDecimal tier1Ratio;

    private BigDecimal tier2Ratio;

    private BigDecimal tier3Ratio;

    private BigDecimal tier4Ratio;

    private BigDecimal weeklyCoolDownThreshold;

    private BigDecimal stopLossPullbackPercent;

    private Instant tier1AddedAt;

    private Instant tier2AddedAt;

    private Instant tier3AddedAt;

    private Instant tier4AddedAt;
}
