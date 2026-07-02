package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 寻优候选参数(ADR-0015 重写,issue #63):搜索维度改为移动止盈参数。
 * 从默认基准({@link TakeProfitParams#broadDefaults}/{@code sectorDefaults})出发扰动:
 * 启动门槛、每次卖出比例、底仓保留比例、冷却期。回撤分级表沿用默认不搜(档位结构是先验,搜易过拟合)。
 *
 * @param activationThreshold 启动门槛(搜索维度)
 * @param pullbackTiers       回撤分级表(沿用默认,不搜)
 * @param sellRatio           每次卖出比例(搜索维度)
 * @param floorRatio          底仓保留比例(搜索维度)
 * @param cooldownDays        卖出冷却交易日数(搜索维度)
 * @param fundCategory        基金类型(fund 不变量,选基准用)
 */
public record OptimizeParams(
        BigDecimal activationThreshold,
        List<PullbackTier> pullbackTiers,
        BigDecimal sellRatio,
        BigDecimal floorRatio,
        int cooldownDays,
        FundCategory fundCategory) {

    /** 转为 {@link TakeProfitParams} 供回测模拟器使用。 */
    public TakeProfitParams toTakeProfitParams() {
        return new TakeProfitParams(activationThreshold, pullbackTiers, sellRatio, floorRatio, cooldownDays);
    }
}