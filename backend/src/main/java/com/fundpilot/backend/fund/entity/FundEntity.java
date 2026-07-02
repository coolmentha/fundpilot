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

    /** 每期定投金额(ADR-0015):基金级用户输入,每月最后交易日扣款;定投无上限、无计划期数。 */
    private BigDecimal dcaAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundSubType fundSubType;

    @Column(length = 64)
    private String benchmarkIndexCode;

    private Instant openedAt;

    /**
     * 持仓成本价(每份成本单价,ADR-0013)。建仓时用户可填(不填默认 T-1 净值);
     * 后续 INCREASE/TRANSFER_IN/INVEST 交易 CONFIRMED 时加权更新。
     * 卖出不改单价;清仓再入场时自然覆盖。
     */
    private BigDecimal costPerShare;

}
