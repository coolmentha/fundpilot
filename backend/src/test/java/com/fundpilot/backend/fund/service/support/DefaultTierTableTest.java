package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #5 验收:DefaultTierTable 4 类型 × 4 档共 16 组回撤阈值,加仓比例四类共用 15/20/25/30%。
 * 回撤阈值用负数(CONTEXT.md「-8%/-15%」原样),净值下跌为负,判定 drawdown <= tierDrawdown 触发加仓。
 * 数据来源:CONTEXT.md「仓位结构」「调节系数」与 issue #5 的 DefaultTierTable 规格。
 */
class DefaultTierTableTest {

    static Stream<Arguments> drawdowns() {
        return Stream.of(
                Arguments.of(FundCategory.BROAD_BASE, 1, "-0.08"),
                Arguments.of(FundCategory.BROAD_BASE, 2, "-0.15"),
                Arguments.of(FundCategory.BROAD_BASE, 3, "-0.25"),
                Arguments.of(FundCategory.BROAD_BASE, 4, "-0.35"),
                Arguments.of(FundCategory.SECTOR, 1, "-0.15"),
                Arguments.of(FundCategory.SECTOR, 2, "-0.25"),
                Arguments.of(FundCategory.SECTOR, 3, "-0.35"),
                Arguments.of(FundCategory.SECTOR, 4, "-0.45"),
                Arguments.of(FundCategory.ACTIVE, 1, "-0.12"),
                Arguments.of(FundCategory.ACTIVE, 2, "-0.20"),
                Arguments.of(FundCategory.ACTIVE, 3, "-0.30"),
                Arguments.of(FundCategory.ACTIVE, 4, "-0.40"),
                Arguments.of(FundCategory.MIXED, 1, "-0.12"),
                Arguments.of(FundCategory.MIXED, 2, "-0.22"),
                Arguments.of(FundCategory.MIXED, 3, "-0.32"),
                Arguments.of(FundCategory.MIXED, 4, "-0.40")
        );
    }

    @ParameterizedTest
    @MethodSource("drawdowns")
    void lookupReturnsExpectedDrawdown(FundCategory category, int tier, String expectedDrawdown) {
        TierDefaults defaults = DefaultTierTable.lookup(category, tier);

        assertThat(defaults.drawdown()).isEqualByComparingTo(new BigDecimal(expectedDrawdown));
    }

    @Test
    void addRatioIsSharedPyramidAcrossCategories() {
        // 四类基金加仓比例统一 15/20/25/30,差异化只体现在回撤阈值上(CONTEXT.md「四档加仓比例」)
        assertThat(DefaultTierTable.lookup(FundCategory.BROAD_BASE, 1).ratio()).isEqualByComparingTo(new BigDecimal("0.15"));
        assertThat(DefaultTierTable.lookup(FundCategory.BROAD_BASE, 2).ratio()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(DefaultTierTable.lookup(FundCategory.BROAD_BASE, 3).ratio()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(DefaultTierTable.lookup(FundCategory.BROAD_BASE, 4).ratio()).isEqualByComparingTo(new BigDecimal("0.30"));
        // 跨类型抽验:混合四档也应 0.30
        assertThat(DefaultTierTable.lookup(FundCategory.MIXED, 4).ratio()).isEqualByComparingTo(new BigDecimal("0.30"));
    }
}
