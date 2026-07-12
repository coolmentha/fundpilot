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
    public static final BigDecimal DEFAULT_MAX_POSITION_RATIO = new BigDecimal("0.30");
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

    /** 单基金仓位上限比例，可向下调整，但数据库硬限制不超过 30%。 */
    @Column(name = "max_position_ratio", nullable = false)
    private BigDecimal maxPositionRatio = DEFAULT_MAX_POSITION_RATIO;

}
