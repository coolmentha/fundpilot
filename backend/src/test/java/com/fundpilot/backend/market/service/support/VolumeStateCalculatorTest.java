package com.fundpilot.backend.market.service.support;

import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.enums.VolumeState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VolumeStateCalculatorTest {

    @Test
    void 数据不足_20_根_返回_empty() {
        IndexKline kline = kline(19, 1000, false);

        assertThat(VolumeStateCalculator.calculate(kline)).isEmpty();
    }

    @Test
    void null_输入_返回_empty() {
        assertThat(VolumeStateCalculator.calculate(null)).isEmpty();
    }

    @Test
    void 最新量大于_1_5_倍均量且下跌_返回_HIGH_DROP() {
        // 前 19 根均量 1000,末根量 2000(=2× 均量)、当日下跌
        IndexKline kline = kline(20, 1000, false, 2000, true);

        Optional<VolumeState> result = VolumeStateCalculator.calculate(kline);

        assertThat(result).contains(VolumeState.HIGH_DROP);
    }

    @Test
    void 最新量大于_1_5_倍均量但上涨_返回_NORMAL() {
        // 放量但上涨,不算放量下跌
        IndexKline kline = kline(20, 1000, false, 2000, false);

        Optional<VolumeState> result = VolumeStateCalculator.calculate(kline);

        assertThat(result).contains(VolumeState.NORMAL);
    }

    @Test
    void 最新量小于_0_5_倍均量_返回_LOW_STABLE() {
        // 末根量 400(=0.4× 均量)
        IndexKline kline = kline(20, 1000, false, 400, false);

        Optional<VolumeState> result = VolumeStateCalculator.calculate(kline);

        assertThat(result).contains(VolumeState.LOW_STABLE);
    }

    @Test
    void 最新量在_0_5_到_1_5_倍之间_返回_NORMAL() {
        IndexKline kline = kline(20, 1000, false, 1000, false);

        Optional<VolumeState> result = VolumeStateCalculator.calculate(kline);

        assertThat(result).contains(VolumeState.NORMAL);
    }

    /** n 根 K 线,每根等量 volume,每根涨/跌由 isDrop 决定。 */
    private static IndexKline kline(int n, long volume, boolean isDrop) {
        return kline(n, volume, isDrop, volume, isDrop);
    }

    /** n-1 根常规量 + 末根自定义量与涨跌方向。 */
    private static IndexKline kline(int n, long baseVolume, boolean baseDrop, long lastVolume, boolean lastDrop) {
        List<IndexKline.Bar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < n - 1; i++) {
            bars.add(bar(start.plusDays(i), baseVolume, baseDrop));
        }
        bars.add(bar(start.plusDays(n - 1), lastVolume, lastDrop));
        return new IndexKline(bars);
    }

    private static IndexKline.Bar bar(LocalDate date, long volume, boolean isDrop) {
        BigDecimal open = new BigDecimal("100.00");
        BigDecimal close = isDrop ? new BigDecimal("99.00") : new BigDecimal("101.00");
        BigDecimal high = open.max(close);
        BigDecimal low = open.min(close);
        return new IndexKline.Bar(date, open, close, high, low, volume);
    }
}
