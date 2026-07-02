package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 寻优 train 集排序单测(issue #63):验证 Calmar 降序 top-k 择优、零回撤 +∞ 优先。
 */
class OptimizeParamRankerTest {

    @Test
    void rankTopK_单调上涨零回撤_优先于有回撤() {
        // 单调上涨 nav:零回撤 Calmar +∞(null) 应优先
        List<BigDecimal> nav = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nav.add(new BigDecimal("1.0").add(new BigDecimal(i).multiply(new BigDecimal("0.01"))));
        }
        List<Instant> dates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            dates.add(Instant.parse("2025-01-01T00:00:00Z").plusSeconds(86400L * i));
        }
        var grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        List<RankedParam> top = OptimizeParamRanker.rankTopK(nav, dates, new BigDecimal("1000"),
                FundCategory.BROAD_BASE, grid, 3);

        assertThat(top).hasSize(3);
        // top1 应是 Calmar +∞(null,零回撤)
        assertThat(top.get(0).trainCalmar()).isNull();
    }

    @Test
    void rankBest_返回Calmar最高一组() {
        List<BigDecimal> nav = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nav.add(new BigDecimal("1.0").add(new BigDecimal(i).multiply(new BigDecimal("0.01"))));
        }
        List<Instant> dates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            dates.add(Instant.parse("2025-01-01T00:00:00Z").plusSeconds(86400L * i));
        }
        var grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        var best = OptimizeParamRanker.rankBest(nav, dates, new BigDecimal("1000"),
                FundCategory.BROAD_BASE, grid);

        assertThat(best).isPresent();
    }

    @Test
    void rankTopK_k为零返回空() {
        var grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);
        List<RankedParam> top = OptimizeParamRanker.rankTopK(
                List.of(new BigDecimal("1.0"), new BigDecimal("1.1")),
                List.of(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-02T00:00:00Z")),
                new BigDecimal("1000"), FundCategory.BROAD_BASE, grid, 0);
        assertThat(top).isEmpty();
    }
}