package com.fundpilot.backend.fund.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link FundSubType} 枚举四值齐全——ADR-0002 数据源维度分类。
 * 这是本期新增枚举,确保值域与 PRD 一致(ETF/INDEX/INDEX_ENHANCED/ACTIVE),
 * 防止后续误删或改名。
 */
class FundSubTypeTest {

    @Test
    void containsAllFourSubTypes() {
        assertThat(FundSubType.values())
                .containsExactlyInAnyOrder(
                        FundSubType.ETF,
                        FundSubType.INDEX,
                        FundSubType.INDEX_ENHANCED,
                        FundSubType.ACTIVE);
    }
}
