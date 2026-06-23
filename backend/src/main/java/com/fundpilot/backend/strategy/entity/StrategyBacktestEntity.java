package com.fundpilot.backend.strategy.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class StrategyBacktestEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_strategy_id")
    private FundStrategyEntity fundStrategyEntity;

    private Instant backtestStartDate;

    private Instant backtestEndDate;

    private BigDecimal strategyReturn;

    private BigDecimal strategyMaxDrawdown;

    private BigDecimal benchmarkHs300Return;

    private BigDecimal benchmarkAllInReturn;

    private BigDecimal benchmarkDcaReturn;

    private BigDecimal benchmarkHs300MaxDrawdown;

    private BigDecimal benchmarkAllInMaxDrawdown;

    private BigDecimal benchmarkDcaMaxDrawdown;

    private boolean passed;
}
