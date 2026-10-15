package com.fundpilot.backend.alerting.domain.grid;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 网格交易提醒的两个比例参数：相对基准线的上浮与下降幅度。
 *
 * <p>与回撤止盈的 {@code TakeProfitParams} 一样，取值范围校验集中在构造器，调用方不重复判断。
 * 两个比例都严格落在 (0, 1)：取 0 会让线路与基准线重合（任何微小波动都命中），取 1 会让下降线归零。
 *
 * @param upRatio   上浮比例，(0, 1)：基准线 × (1 + upRatio) 即上线路
 * @param downRatio 下降比例，(0, 1)：基准线 × (1 - downRatio) 即下线路
 */
public record GridParams(BigDecimal upRatio, BigDecimal downRatio) {

    public GridParams {
        upRatio = ratio("上浮比例", upRatio);
        downRatio = ratio("下降比例", downRatio);
    }

    /** 比例取值校验：必须严格落在 (0, 1)。 */
    private static BigDecimal ratio(String label, BigDecimal value) {
        Objects.requireNonNull(value, label + "不能为空");
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(label + "必须严格落在 0 到 1 之间（不含两端）");
        }
        return value;
    }
}
