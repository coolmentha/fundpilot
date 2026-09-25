package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeriesStatisticsTest {

    @Test
    void 窗口内最大值与最小值含当前位置() {
        List<BigDecimal> values = values("1", "3", "2", "5");

        assertThat(SeriesStatistics.trailingMaximum(values, 2))
                .containsExactly(null, new BigDecimal("3"), new BigDecimal("3"), new BigDecimal("5"));
        assertThat(SeriesStatistics.trailingMinimum(values, 2))
                .containsExactly(null, new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("2"));
    }

    @Test
    void 窗口内平均值含当前位置() {
        assertThat(SeriesStatistics.trailingAverage(values("1", "2", "3", "4"), 2))
                .containsExactly(null, new BigDecimal("1.5"), new BigDecimal("2.5"), new BigDecimal("3.5"));
    }

    @Test
    void 分位为小于等于当前值的样本占比() {
        List<BigDecimal> samples = values("1", "2", "3", "4");

        assertThat(SeriesStatistics.percentileRank(samples, new BigDecimal("2")))
                .isEqualByComparingTo("0.5");
        assertThat(SeriesStatistics.percentileRank(samples, new BigDecimal("4")))
                .isEqualByComparingTo("1");
        assertThat(SeriesStatistics.percentileRank(samples, new BigDecimal("0")))
                .isEqualByComparingTo("0");
    }

    @Test
    void 空样本与非正窗口被拒绝() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SeriesStatistics.percentileRank(List.of(), BigDecimal.ONE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SeriesStatistics.trailingMaximum(values("1"), 0));
    }

    private static List<BigDecimal> values(String... raw) {
        return Arrays.stream(raw).map(BigDecimal::new).toList();
    }
}