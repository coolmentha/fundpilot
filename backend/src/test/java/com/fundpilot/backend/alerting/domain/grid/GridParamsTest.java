package com.fundpilot.backend.alerting.domain.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 网格两参数的取值范围校验：两个比例都必须严格落在 (0, 1)。 */
class GridParamsTest {

    @Test
    void 合法参数原样保留() {
        GridParams params = params("0.05", "0.03");

        assertThat(params.upRatio()).isEqualByComparingTo("0.05");
        assertThat(params.downRatio()).isEqualByComparingTo("0.03");
    }

    @Test
    void 两个比例都不允许取零() {
        assertThatThrownBy(() -> params("0", "0.03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("0.05", "0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 两个比例都不允许取一() {
        assertThatThrownBy(() -> params("1", "0.03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("0.05", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 负数与超过一的比例被拒绝() {
        assertThatThrownBy(() -> params("-0.05", "0.03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("1.5", "0.03")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 参数缺失时拒绝构造() {
        assertThatThrownBy(() -> new GridParams(null, new BigDecimal("0.03")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GridParams(new BigDecimal("0.05"), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static GridParams params(String upRatio, String downRatio) {
        return new GridParams(new BigDecimal(upRatio), new BigDecimal(downRatio));
    }
}
