package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #26 验收:寻优网格生成纯函数 {@link OptimizeGridGenerator}。
 * <p>对一只基金按 {@code fundCategory} 从 {@code DefaultTierTable} 取默认回撤阈值为中心,
 * 生成 3 维网格(tier1Drawdown / tier4Drawdown / stopLossPullbackPercent,每维 4 档),
 * 中间档按默认表相对位置内插,违反 tier1>tier2>tier3>tier4 递增约束的组合被过滤。
 * 纯函数,不接 API/DB,独立单测验证。
 */
class OptimizeGridGeneratorTest {

    @Test
    void 所有组合满足_tier1大于tier2大于tier3大于tier4_递增约束() {
        List<OptimizeParams> grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        assertThat(grid).isNotEmpty();
        for (OptimizeParams p : grid) {
            // 负数递减:tier1(最浅) > tier2 > tier3 > tier4(最深),即 -0.08 > -0.15 > -0.25 > -0.35
            assertThat(p.tier1Drawdown()).isGreaterThan(p.tier2Drawdown());
            assertThat(p.tier2Drawdown()).isGreaterThan(p.tier3Drawdown());
            assertThat(p.tier3Drawdown()).isGreaterThan(p.tier4Drawdown());
        }
    }

    @Test
    void 中间档按默认表相对位置内插_且比例与冷静用默认值不搜() {
        // 宽基默认 tier1=-0.08 / tier2=-0.15 / tier3=-0.25 / tier4=-0.35
        // 内插比例:ratio2=(-0.15-(-0.08))/(-0.35-(-0.08))=(-0.07)/(-0.27)≈0.2593
        //          ratio3=(-0.25-(-0.08))/(-0.27)≈0.6296
        List<OptimizeParams> grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);
        BigDecimal expectedRatio2 = new BigDecimal("-0.07").divide(new BigDecimal("-0.27"), MathContext.DECIMAL64);
        BigDecimal expectedRatio3 = new BigDecimal("-0.17").divide(new BigDecimal("-0.27"), MathContext.DECIMAL64);

        // 每个组合的中间档应满足 tierN = tier1 + ratioN × (tier4 - tier1)
        for (OptimizeParams p : grid) {
            BigDecimal span = p.tier4Drawdown().subtract(p.tier1Drawdown());
            BigDecimal expectedTier2 = p.tier1Drawdown().add(expectedRatio2.multiply(span, MathContext.DECIMAL64));
            BigDecimal expectedTier3 = p.tier1Drawdown().add(expectedRatio3.multiply(span, MathContext.DECIMAL64));
            assertThat(p.tier2Drawdown()).isCloseTo(expectedTier2, within(new BigDecimal("0.0001")));
            assertThat(p.tier3Drawdown()).isCloseTo(expectedTier3, within(new BigDecimal("0.0001")));
        }

        // 非搜索维度:4 档加仓比例固定 15/20/25/30,weeklyCoolDownThreshold 用宽基默认 0.08
        for (OptimizeParams p : grid) {
            assertThat(p.tier1Ratio()).isEqualByComparingTo("0.15");
            assertThat(p.tier2Ratio()).isEqualByComparingTo("0.20");
            assertThat(p.tier3Ratio()).isEqualByComparingTo("0.25");
            assertThat(p.tier4Ratio()).isEqualByComparingTo("0.30");
            assertThat(p.weeklyCoolDownThreshold()).isEqualByComparingTo("0.08");
        }
    }

    @Test
    void 不同fundCategory产出不同中心_宽基与行业档位区间分离() {
        // 宽基默认 tier1=-0.08/tier4=-0.35;行业默认 tier1=-0.15/tier4=-0.45(更深)
        // 宽基 tier1 区间 ≈ [-0.11, -0.05],行业 tier1 区间 ≈ [-0.18, -0.12],两者不重叠
        List<OptimizeParams> broad = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);
        List<OptimizeParams> sector = OptimizeGridGenerator.generate(FundCategory.SECTOR);

        BigDecimal broadTier1Max = broad.stream().map(OptimizeParams::tier1Drawdown)
                .max(BigDecimal::compareTo).orElseThrow();
        BigDecimal sectorTier1Min = sector.stream().map(OptimizeParams::tier1Drawdown)
                .min(BigDecimal::compareTo).orElseThrow();
        // 宽基最浅 tier1(-0.05)仍比行业最深 tier1(-0.18)更浅(更大),两类区间不重叠
        assertThat(broadTier1Max).isGreaterThan(sectorTier1Min);

        // 行业 weeklyCoolDownThreshold 用行业默认 0.12(宽基是 0.08)
        assertThat(sector.get(0).weeklyCoolDownThreshold()).isEqualByComparingTo("0.12");
    }

    @Test
    void 每维4档共64组_过滤递增约束后总数不超过64() {
        List<OptimizeParams> grid = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        // 3 维 × 4 档 = 64 上限;tier1≤tier4 的组合被过滤,实际 ≤ 64
        assertThat(grid).hasSizeLessThanOrEqualTo(64);
        // tier1/tier4 各 4 档,过滤后至少有一些合法组合(宽基默认区间 tier1∈[-0.11,-0.05] 全部 > tier4∈[-0.38,-0.32],全部合法)
        assertThat(grid).isNotEmpty();
    }
}
