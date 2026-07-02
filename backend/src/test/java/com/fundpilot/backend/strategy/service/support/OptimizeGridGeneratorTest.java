package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 寻优网格生成单测(issue #63):验证按 fundCategory 从默认基准扰动生成候选网格。
 */
class OptimizeGridGeneratorTest {

    @Test
    void 宽基生成网格_含默认基准_数量大于1() {
        var grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        assertThat(grid).isNotEmpty();
        assertThat(grid.size()).isGreaterThan(1);
        // 含默认基准(启动门槛0.50、卖出0.20、冷却20)
        assertThat(grid).anySatisfy(p -> {
            assertThat(p.activationThreshold()).isEqualByComparingTo("0.50");
            assertThat(p.sellRatio()).isEqualByComparingTo("0.20");
            assertThat(p.cooldownDays()).isEqualTo(20);
            assertThat(p.fundCategory()).isEqualTo(FundCategory.BROAD_BASE);
        });
    }

    @Test
    void 行业生成网格_用行业默认基准() {
        var grid = OptimizeGridGenerator.generate(FundCategory.SECTOR);

        assertThat(grid).isNotEmpty();
        // 含行业默认(启动门槛0.40、卖出0.25)
        assertThat(grid).anySatisfy(p -> {
            assertThat(p.activationThreshold()).isEqualByComparingTo("0.40");
            assertThat(p.sellRatio()).isEqualByComparingTo("0.25");
        });
    }

    @Test
    void 网格候选启动门槛与卖出比例均为正() {
        var grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        assertThat(grid).allSatisfy(p -> {
            assertThat(p.activationThreshold()).isPositive();
            assertThat(p.sellRatio()).isPositive();
            assertThat(p.cooldownDays()).isGreaterThanOrEqualTo(1);
        });
    }
}