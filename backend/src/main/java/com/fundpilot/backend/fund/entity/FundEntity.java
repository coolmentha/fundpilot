package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "fund")
@SQLDelete(sql = "UPDATE fund SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundEntity extends AbstractEntity {
    public static final BigDecimal DEFAULT_POSITION_WARNING_RATIO = new BigDecimal("0.30");
    @Column(name = "owner_id")
    private Long ownerId;
    @Column(name = "product_id")
    private Long productId;
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

    /** 是否展示该基金的当前持仓占比提醒。 */
    @Column(name = "position_warning_enabled", nullable = false)
    private boolean positionWarningEnabled = true;

    /** 单基金当前持仓占比提醒线；仅提示，范围为 (0, 1]。 */
    @Column(name = "position_warning_ratio", nullable = false)
    private BigDecimal positionWarningRatio = DEFAULT_POSITION_WARNING_RATIO;

    @ManyToMany
    @JoinTable(name = "fund_group_member",
            joinColumns = @JoinColumn(name = "fund_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<FundGroupEntity> groups = new LinkedHashSet<>();

}
