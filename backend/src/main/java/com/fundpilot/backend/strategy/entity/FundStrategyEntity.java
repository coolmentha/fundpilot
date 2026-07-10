package com.fundpilot.backend.strategy.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 策略参数实体(卖出纪律专用)。
 * <p>保存定投止盈配置版本、推荐来源和当前止盈周期状态。
 * 逻辑止损不需要可配参数(ETF 看跟踪指数放量下跌,ACTIVE 看破年线+MACD,均为硬编码规则)。
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

    /** 止盈周期高点回撤幅度，正数比例。保留旧字段名以兼容存量 API。 */
    private BigDecimal stopLossPullbackPercent;

    private BigDecimal profitActivationPercent;

    private BigDecimal profitHarvestPercent;

    private BigDecimal minimumHoldingPercent;

    private BigDecimal maxSingleSellPercent;

    private Integer cooldownTradingDays;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundCategory presetFundCategory;

    private Integer presetVersion;

    private boolean customized;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TakeProfitPhase takeProfitPhase;

    private Instant cycleStartedAt;

    private BigDecimal cyclePeakNav;

    private Long triggeredSignalId;

    private Instant cooldownStartedAt;
}
