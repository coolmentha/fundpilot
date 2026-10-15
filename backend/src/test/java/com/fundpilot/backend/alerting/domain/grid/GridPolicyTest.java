package com.fundpilot.backend.alerting.domain.grid;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 网格判定口径：边界取值、方向互斥、无效入参。 */
class GridPolicyTest {

    private static final GridParams PARAMS = new GridParams(new BigDecimal("0.05"), new BigDecimal("0.03"));

    @Test
    void 估值等于上线路时命中卖出() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("1.0500"), PARAMS))
                .isEqualTo(GridDirection.SELL);
    }

    @Test
    void 估值高于上线路时命中卖出() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("1.2000"), PARAMS))
                .isEqualTo(GridDirection.SELL);
    }

    @Test
    void 估值仅差一点点未达上线路时不命中() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("1.0499"), PARAMS)).isNull();
    }

    @Test
    void 估值等于下线路时命中买入() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("0.9700"), PARAMS))
                .isEqualTo(GridDirection.BUY);
    }

    @Test
    void 估值低于下线路时命中买入() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("0.9000"), PARAMS))
                .isEqualTo(GridDirection.BUY);
    }

    @Test
    void 估值仅差一点点未达下线路时不命中() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("0.9701"), PARAMS)).isNull();
    }

    @Test
    void 估值等于基准线落在带内不命中() {
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0000"), new BigDecimal("1.0000"), PARAMS)).isNull();
    }

    @Test
    void 非正的基准线或估值一律不命中() {
        assertThat(GridPolicy.evaluate(null, new BigDecimal("1.2"), PARAMS)).isNull();
        assertThat(GridPolicy.evaluate(new BigDecimal("0"), new BigDecimal("1.2"), PARAMS)).isNull();
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0"), null, PARAMS)).isNull();
        assertThat(GridPolicy.evaluate(new BigDecimal("1.0"), new BigDecimal("0"), PARAMS)).isNull();
    }

    @Test
    void 上下线路按各自比例独立计算() {
        assertThat(GridPolicy.upperLine(new BigDecimal("1.0000"), PARAMS)).isEqualByComparingTo("1.0500");
        assertThat(GridPolicy.lowerLine(new BigDecimal("1.0000"), PARAMS)).isEqualByComparingTo("0.9700");
    }

    @Test
    void 非对称比例下线路间距可以不同于上线路() {
        GridParams asymmetric = new GridParams(new BigDecimal("0.10"), new BigDecimal("0.02"));

        assertThat(GridPolicy.upperLine(new BigDecimal("2.0000"), asymmetric)).isEqualByComparingTo("2.2000");
        assertThat(GridPolicy.lowerLine(new BigDecimal("2.0000"), asymmetric)).isEqualByComparingTo("1.9600");
        // 上线路更远：+8% 不命中卖出，但 -3% 命中买入
        assertThat(GridPolicy.evaluate(new BigDecimal("2.0000"), new BigDecimal("2.1600"), asymmetric)).isNull();
        assertThat(GridPolicy.evaluate(new BigDecimal("2.0000"), new BigDecimal("1.9400"), asymmetric))
                .isEqualTo(GridDirection.BUY);
    }

    @Test
    void 偏离比例以基准线为分母() {
        assertThat(GridPolicy.deviation(new BigDecimal("1.0000"), new BigDecimal("1.0500")))
                .isEqualByComparingTo("0.05");
        assertThat(GridPolicy.deviation(new BigDecimal("1.0000"), new BigDecimal("0.9700")))
                .isEqualByComparingTo("-0.03");
        assertThat(GridPolicy.deviation(new BigDecimal("2.0000"), new BigDecimal("2.2000")))
                .isEqualByComparingTo("0.1");
    }

    @Test
    void 无效入参的偏离比例为无值() {
        assertThat(GridPolicy.deviation(null, new BigDecimal("1.0"))).isNull();
        assertThat(GridPolicy.deviation(new BigDecimal("1.0"), null)).isNull();
    }
}
