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
 * 策略参数实体(ADR-0015 重写):移动止盈参数,废弃择时金字塔字段(tier1~4Drawdown/Ratio/AddedAt、
 * weeklyCoolDownThreshold、stopLossPullbackPercent)。
 * <p>回撤分级表用 pullbackTierN_* 至多 4 档存储(pullbackTierCount 标记有效档数);宽基 3 档、行业 4 档。
 *
 * @param activationThreshold   启动门槛(宽基 0.50、行业 0.40):收益率达此值才开始追踪止盈线
 * @param pullbackTierCount     回撤分级表有效档数(2~4)
 * @param pullbackTier1Yield    一档起算历史最高收益率(对齐启动门槛)
 * @param pullbackTier1Ratio    一档回撤比例
 * @param pullbackTier2Yield    二档起算收益率
 * @param pullbackTier2Ratio    二档回撤比例
 * @param pullbackTier3Yield    三档起算收益率
 * @param pullbackTier3Ratio    三档回撤比例
 * @param pullbackTier4Yield    四档起算收益率(行业用;宽基留 null)
 * @param pullbackTier4Ratio    四档回撤比例(行业用;宽基留 null)
 * @param sellRatio             每次卖出占当前持仓比例(宽基 0.20、行业 0.25)
 * @param floorRatio            底仓保留比例(宽基 0.40、行业 0.25)
 * @param cooldownDays          卖出冷却交易日数(20):卖出后该天数内不再判定止盈
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

    private BigDecimal activationThreshold;

    private Integer pullbackTierCount;

    private BigDecimal pullbackTier1Yield;
    private BigDecimal pullbackTier1Ratio;
    private BigDecimal pullbackTier2Yield;
    private BigDecimal pullbackTier2Ratio;
    private BigDecimal pullbackTier3Yield;
    private BigDecimal pullbackTier3Ratio;
    private BigDecimal pullbackTier4Yield;
    private BigDecimal pullbackTier4Ratio;

    private BigDecimal sellRatio;
    private BigDecimal floorRatio;
    private Integer cooldownDays;
}