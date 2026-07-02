package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 寻优网格生成(ADR-0015 重写,issue #63):从默认基准出发,对启动门槛/卖出比例/底仓比例/冷却期
 * 各维度扰动生成候选网格。回撤分级表沿用默认不搜(档位结构是先验,搜易过拟合)。
 * <p>极端行情保护阈值(单日7%/3日12%)是 {@link TrailingStopEngine} 的策略级常量,本期不纳入搜索
 * (避免与 #59 实现脱节;若后续需搜,应先将阈值提升为 {@link TakeProfitParams} 字段)。
 * <p>纯函数,不接 API/DB/前端。
 */
public final class OptimizeGridGenerator {

    private OptimizeGridGenerator() {
    }

    /** 每维扰动的偏移量(相对默认值的增减比例点,如 ±0.05 表 ±5个百分点)。 */
    private static final BigDecimal[] THRESHOLD_OFFSETS = {
            new BigDecimal("-0.05"), BigDecimal.ZERO, new BigDecimal("0.05")
    };
    private static final BigDecimal[] SELL_RATIO_OFFSETS = {
            new BigDecimal("-0.05"), BigDecimal.ZERO, new BigDecimal("0.05")
    };
    private static final BigDecimal[] FLOOR_RATIO_OFFSETS = {
            new BigDecimal("-0.05"), BigDecimal.ZERO, new BigDecimal("0.05")
    };
    private static final int[] COOLDOWN_OFFSETS = {-5, 0, 5};

    /**
     * 按 {@code fundCategory} 选默认基准,生成 3×3×3×3=81 组候选(启动门槛×卖出比例×底仓比例×冷却期)。
     * 回撤分级表沿用默认。各维度扰动后 clamp 到合理区间(>0、<1)。
     */
    public static List<OptimizeParams> generate(FundCategory category) {
        TakeProfitParams base = category == FundCategory.SECTOR
                ? TakeProfitParams.sectorDefaults() : TakeProfitParams.broadDefaults();
        List<OptimizeParams> grid = new ArrayList<>();
        for (BigDecimal thOff : THRESHOLD_OFFSETS) {
            BigDecimal threshold = base.activationThreshold().add(thOff);
            if (threshold.signum() <= 0) {
                continue;
            }
            for (BigDecimal sellOff : SELL_RATIO_OFFSETS) {
                BigDecimal sellRatio = base.sellRatio().add(sellOff);
                if (sellRatio.signum() <= 0 || sellRatio.compareTo(BigDecimal.ONE) >= 0) {
                    continue;
                }
                for (BigDecimal floorOff : FLOOR_RATIO_OFFSETS) {
                    BigDecimal floorRatio = base.floorRatio().add(floorOff);
                    if (floorRatio.signum() <= 0 || floorRatio.compareTo(BigDecimal.ONE) >= 0) {
                        continue;
                    }
                    for (int cdOff : COOLDOWN_OFFSETS) {
                        int cooldown = Math.max(1, base.cooldownDays() + cdOff);
                        grid.add(new OptimizeParams(threshold, base.pullbackTiers(), sellRatio,
                                floorRatio, cooldown, category));
                    }
                }
            }
        }
        return grid;
    }
}