package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fund")
@SQLDelete(sql = "UPDATE fund SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundEntity extends AbstractEntity {
    private String fundCode;

    private String fundName;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private InvestmentTarget investmentTarget;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private OperationMode operationMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private InvestmentPhilosophy investmentPhilosophy;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundCategory fundCategory;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundStatus status = FundStatus.PENDING_HOLDING;

    private BigDecimal plannedTotalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundSubType fundSubType;

    @Column(length = 64)
    private String benchmarkIndexCode;

    private Instant openedAt;

}
