package com.fundpilot.backend.alerting.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 回撤止盈六参数的取值范围校验；区间口径照搬旧纪律策略。 */
class TakeProfitParamsTest {

    @Test
    void 合法参数原样保留() {
        TakeProfitParams params = params("0.15", "0.06", "0.50", "0.50", "0.20", 10);

        assertThat(params.activation()).isEqualByComparingTo("0.15");
        assertThat(params.pullback()).isEqualByComparingTo("0.06");
        assertThat(params.harvest()).isEqualByComparingTo("0.50");
        assertThat(params.minimumHolding()).isEqualByComparingTo("0.50");
        assertThat(params.maxSingleSell()).isEqualByComparingTo("0.20");
        assertThat(params.cooldownDays()).isEqualTo(10);
    }

    @Test
    void 上界允许取到一的参数() {
        TakeProfitParams params = params("0.15", "0.06", "1", "0.50", "1", 0);

        assertThat(params.harvest()).isEqualByComparingTo("1");
        assertThat(params.maxSingleSell()).isEqualByComparingTo("1");
        assertThat(params.cooldownDays()).isZero();
    }

    @Test
    void 启动收益率必须落在零到一之间且允许取一() {
        assertThat(params("1", "0.06", "0.50", "0.50", "0.20", 10).activation())
                .isEqualByComparingTo("1");
        assertThatThrownBy(() -> params("0", "0.06", "0.50", "0.50", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("1.1", "0.06", "0.50", "0.50", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 回撤比例必须严格落在零到一之间() {
        assertThatThrownBy(() -> params("0.15", "0", "0.50", "0.50", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("0.15", "1", "0.50", "0.50", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 最低保留允许取零但不允许取满仓() {
        assertThat(params("0.15", "0.06", "0.50", "0", "0.20", 10).minimumHolding())
                .isEqualByComparingTo("0");
        assertThatThrownBy(() -> params("0.15", "0.06", "0.50", "1", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("0.15", "0.06", "0.50", "-0.1", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 收割比例与单次上限不允许取零() {
        assertThatThrownBy(() -> params("0.15", "0.06", "0", "0.50", "0.20", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("0.15", "0.06", "0.50", "0.50", "0", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 冷静期交易日限定在零到二百五十之间() {
        assertThat(params("0.15", "0.06", "0.50", "0.50", "0.20", 250).cooldownDays()).isEqualTo(250);
        assertThatThrownBy(() -> params("0.15", "0.06", "0.50", "0.50", "0.20", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params("0.15", "0.06", "0.50", "0.50", "0.20", 251))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 参数缺失时拒绝构造() {
        assertThatThrownBy(() -> new TakeProfitParams(null, new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10))
                .isInstanceOf(NullPointerException.class);
    }

    private static TakeProfitParams params(String activation, String pullback, String harvest,
                                           String minimumHolding, String maxSingleSell, int cooldownDays) {
        return new TakeProfitParams(new BigDecimal(activation), new BigDecimal(pullback),
                new BigDecimal(harvest), new BigDecimal(minimumHolding), new BigDecimal(maxSingleSell),
                cooldownDays);
    }
}