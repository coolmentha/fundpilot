package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #5 验收:DefaultCoolDownTable 单周跌幅冷静阈值默认值。
 * 宽基 8% / 行业 12% / 主动 10% / 混合 10%(CONTEXT.md「单周跌幅冷静」)。
 * 阈值用正数小数表示跌幅幅度,超过即触发 WEEKLY_COOLDOWN 强提示。
 */
class DefaultCoolDownTableTest {

    static Stream<Arguments> coolDownThresholds() {
        return Stream.of(
                Arguments.of(FundCategory.BROAD_BASE, "0.08"),
                Arguments.of(FundCategory.SECTOR, "0.12"),
                Arguments.of(FundCategory.ACTIVE, "0.10"),
                Arguments.of(FundCategory.MIXED, "0.10")
        );
    }

    @ParameterizedTest
    @MethodSource("coolDownThresholds")
    void lookupReturnsExpectedCoolDownThreshold(FundCategory category, String expected) {
        assertThat(DefaultCoolDownTable.lookup(category)).isEqualByComparingTo(new BigDecimal(expected));
    }
}
