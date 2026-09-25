package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovingAverageTest {

    @Test
    void 简单均线取窗口内取值算术平均() {
        List<BigDecimal> average = MovingAverage.simple(values("1", "2", "3", "4", "5"), 3);

        assertThat(average.get(0)).isNull();
        assertThat(average.get(1)).isNull();
        assertThat(average.get(2)).isEqualByComparingTo("2");
        assertThat(average.get(3)).isEqualByComparingTo("3");
        assertThat(average.get(4)).isEqualByComparingTo("4");
    }

    @Test
    void 窗口大于序列长度时全部为空() {
        assertThat(MovingAverage.simple(values("1", "2"), 3)).containsOnlyNulls();
    }

    @Test
    void 非正窗口被拒绝() {
        assertThatIllegalArgumentException().isThrownBy(() -> MovingAverage.simple(values("1"), 0));
    }

    private static List<BigDecimal> values(String... raw) {
        return java.util.Arrays.stream(raw).map(BigDecimal::new).toList();
    }
}