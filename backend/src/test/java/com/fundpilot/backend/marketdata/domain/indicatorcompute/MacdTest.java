package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MacdTest {

    @Test
    void 指数移动平均首值取序列首点() {
        double[] ema = Macd.ema(values("1", "2", "3"), 3);

        assertThat(ema[0]).isEqualTo(1.0);
        assertThat(ema[1]).isCloseTo(1.5, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(ema[2]).isCloseTo(2.25, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 单调上行序列末柱为正() {
        Macd.Line line = Macd.compute(values("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), 2, 4, 2);

        assertThat(line.histogram()[0]).isZero();
        assertThat(line.histogram()[9]).isGreaterThan(0);
    }

    @Test
    void 先跌后涨序列柱由负转正() {
        Macd.Line line = Macd.compute(values("10", "9", "8", "7", "8", "9", "10", "11", "12"), 2, 4, 2);

        assertThat(line.histogram()[3]).isLessThan(0);
        assertThat(line.histogram()[8]).isGreaterThan(0);
    }

    @Test
    void 周期非法被拒绝() {
        List<BigDecimal> values = values("1", "2", "3");
        assertThatIllegalArgumentException().isThrownBy(() -> Macd.compute(values, 26, 12, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> Macd.compute(values, 0, 26, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> Macd.ema(List.of(), 12));
    }

    private static List<BigDecimal> values(String... raw) {
        return Arrays.stream(raw).map(BigDecimal::new).toList();
    }
}