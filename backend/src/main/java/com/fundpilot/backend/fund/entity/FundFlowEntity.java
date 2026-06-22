package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.enums.FundFlowSource;
import com.fundpilot.backend.fund.enums.FundFlowStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class FundFlowEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)  // 必须用 LAZY，绝不用 EAGER
    @JoinColumn(name = "fund_id", nullable = false) // 数据库外键列名
    private FundEntity fundEntity;

    private BigDecimal amount;

    private Instant confirmTime;

    private Instant cancelTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundFlowStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundFlowSource source;

    private BigDecimal shares;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_flow_id")
    private FundFlowEntity relatedFundFlowEntity;
}
