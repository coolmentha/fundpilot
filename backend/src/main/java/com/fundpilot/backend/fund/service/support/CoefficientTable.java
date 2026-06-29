package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 调节系数表:年线 / 周 MACD / 成交量三维度独立查表(CONTEXT.md「调节系数表」)。
 * <p>年线 3 档:上方且向上 1.0 / 上方但向下 0.7 / 下方且向下 0.4。
 * 周 MACD 4 档:底背离 1.2 / 绿柱缩小 1.0 / 红柱缩小 0.9 / 绿柱扩大 0.6。
 * 成交量 3 档:地量企稳 1.2 / 正常 1.0 / 放量下跌 0.5。
 * 三维度系数交给 {@link CoefficientCombiner} 相乘再 clamp(0.3, 1.5)。
 */
public final class CoefficientTable {

    private static final Map<YearLineState, BigDecimal> YEAR_LINE = Map.of(
            YearLineState.ABOVE_RISING, new BigDecimal("1.0"),
            YearLineState.ABOVE_FALLING, new BigDecimal("0.7"),
            YearLineState.BELOW_FALLING, new BigDecimal("0.4")
    );

    private static final Map<WeeklyMacdState, BigDecimal> MACD = Map.of(
            WeeklyMacdState.DIVERGENCE_BOTTOM, new BigDecimal("1.2"),
            WeeklyMacdState.GREEN_SHRINKING, new BigDecimal("1.0"),
            WeeklyMacdState.RED_SHRINKING, new BigDecimal("0.9"),
            WeeklyMacdState.GREEN_EXPANDING, new BigDecimal("0.6")
    );

    private static final Map<VolumeState, BigDecimal> VOLUME = Map.of(
            VolumeState.LOW_STABLE, new BigDecimal("1.2"),
            VolumeState.NORMAL, new BigDecimal("1.0"),
            VolumeState.HIGH_DROP, new BigDecimal("0.5")
    );

    private CoefficientTable() {
    }

    public static BigDecimal yearLine(YearLineState state) {
        return YEAR_LINE.get(state);
    }

    public static BigDecimal macd(WeeklyMacdState state) {
        return MACD.get(state);
    }

    public static BigDecimal volume(VolumeState state) {
        return VOLUME.get(state);
    }
}
