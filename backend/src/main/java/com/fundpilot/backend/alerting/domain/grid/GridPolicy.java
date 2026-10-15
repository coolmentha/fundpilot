package com.fundpilot.backend.alerting.domain.grid;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 网格判定的纯函数：给出基准线与当日盘中估值，判断是否越线以及方向。
 *
 * <p>不持有状态、不访问外部依赖，因此可以脱离数据库与邮件通道直接单测。
 *
 * <p>判定口径：{@code 估值 >= 基准 × (1 + 上浮)} 命中 {@link GridDirection#SELL}；
 * {@code 估值 <= 基准 × (1 - 下降)} 命中 {@link GridDirection#BUY}。两线互斥，至多命中一个方向。
 */
public final class GridPolicy {

    /** 偏离比例的展示与比较精度：4 位小数足够区分万分之一的价格差异。 */
    private static final int RATIO_SCALE = 4;

    private static final MathContext MATH = MathContext.DECIMAL64;

    private GridPolicy() {
    }

    /**
     * 判断当前估值是否越过网格线。
     *
     * @param baselineNav    基准线：最近一条有成交净值的已确认流水净值，必须为正
     * @param valuationNav   当日盘中估值，必须为正
     * @param params         上下浮比例
     * @return 命中方向；未越线返回 {@code null}
     */
    public static GridDirection evaluate(BigDecimal baselineNav, BigDecimal valuationNav, GridParams params) {
        if (!positive(baselineNav) || !positive(valuationNav)) {
            return null;
        }
        if (valuationNav.compareTo(upperLine(baselineNav, params)) >= 0) {
            return GridDirection.SELL;
        }
        if (valuationNav.compareTo(lowerLine(baselineNav, params)) <= 0) {
            return GridDirection.BUY;
        }
        return null;
    }

    /** 上线路 = 基准线 × (1 + 上浮比例)。 */
    public static BigDecimal upperLine(BigDecimal baselineNav, GridParams params) {
        return baselineNav.multiply(BigDecimal.ONE.add(params.upRatio()), MATH);
    }

    /** 下线路 = 基准线 × (1 - 下降比例)。 */
    public static BigDecimal lowerLine(BigDecimal baselineNav, GridParams params) {
        return baselineNav.multiply(BigDecimal.ONE.subtract(params.downRatio()), MATH);
    }

    /**
     * 当前估值相对基准线的偏离比例，如 {@code 0.0523} 表示高于基准线 5.23%。
     *
     * @return 偏离比例；任一入参非正时返回 {@code null}
     */
    public static BigDecimal deviation(BigDecimal baselineNav, BigDecimal valuationNav) {
        if (!positive(baselineNav) || !positive(valuationNav)) {
            return null;
        }
        return valuationNav.subtract(baselineNav).divide(baselineNav, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
