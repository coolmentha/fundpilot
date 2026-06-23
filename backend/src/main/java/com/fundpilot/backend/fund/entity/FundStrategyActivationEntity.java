package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class FundStrategyActivationEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_strategy_id")
    private FundStrategyEntity fundStrategyEntity;

    private Instant activatedAt;

    private Instant deactivatedAt;
}
