package com.fundpilot.backend.strategy.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;

/**
 * 策略参数实体(卖出纪律专用)。
 * <p>金字塔加仓机制移除后,本实体只保留移动止盈阈值(stopLossPullbackPercent)与版本状态机(status)。
 * 逻辑止损不再需要可配参数(ETF 看跟踪指数放量下跌,ACTIVE 看破年线+MACD,均为硬编码规则)。
 */
@Entity
@Table(name = "fund_strategy")
@SQLDelete(sql = "UPDATE fund_strategy SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundStrategyEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StrategyParamStatus status;

    /** 移动止盈回落幅度:从持有期高点回落 n×本阈值触发卖 holdingShares×(n/4)。 */
    private BigDecimal stopLossPullbackPercent;
}
