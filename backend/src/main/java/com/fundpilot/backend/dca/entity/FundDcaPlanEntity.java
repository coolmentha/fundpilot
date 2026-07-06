package com.fundpilot.backend.dca.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.fund.entity.FundEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;

/**
 * 定投计划实体:用户配置的自动定投计划。
 * <p>EFFECTIVE 状态的计划由 DcaSuggestionJob 在定投日自动生成 INVEST 交易(PENDING)。
 * 同基金同时最多一份 EFFECTIVE(数据库 uq_fund_dca_plan_effective 兜底)。
 */
@Entity
@Table(name = "fund_dca_plan")
@SQLDelete(sql = "UPDATE fund_dca_plan SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundDcaPlanEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    /** 是否启用(EFFECTIVE 状态下若 false 则 Job 跳过)。 */
    private Boolean enabled;

    /** 每次定投金额(元)。 */
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private DcaFrequency frequency;

    /** 周定投日(1=周一...7=周日),WEEKLY 时必填。 */
    private Integer dayOfWeek;

    /** 月定投日(1-28),MONTHLY 时必填。 */
    private Integer dayOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private DcaPlanStatus status;
}
