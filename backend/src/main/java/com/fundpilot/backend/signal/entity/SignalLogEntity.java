package com.fundpilot.backend.signal.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.valueobject.Measure;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "signal_log")
@SQLDelete(sql = "UPDATE signal_log SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class SignalLogEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_strategy_id")
    private FundStrategyEntity fundStrategyEntity;

    private Instant signalDate;

    private BigDecimal triggerNav;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SignalType signalType;

    @Embedded
    private Measure suggestedMeasure;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SignalReason reason;

    private String warnings;
}
