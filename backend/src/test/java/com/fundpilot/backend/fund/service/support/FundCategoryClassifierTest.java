package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #144:行业指数基金(名称命中半导体/光伏/白酒 等 BenchmarkIndexTable 行业词)
 * 必须判 SECTOR,而非被误判为 BROAD_BASE。
 */
class FundCategoryClassifierTest {

    @Test
    void 宽基指数ETF_判BROAD_BASE() {
        assertThat(FundCategoryClassifier.classify("易方达沪深300ETF", FundSubType.ETF))
                .isEqualTo(FundCategory.BROAD_BASE);
    }

    @Test
    void 行业指数ETF_判SECTOR() {
        assertThat(FundCategoryClassifier.classify("华夏国证半导体芯片ETF", FundSubType.ETF))
                .isEqualTo(FundCategory.SECTOR);
        assertThat(FundCategoryClassifier.classify("华泰柏瑞光伏ETF", FundSubType.ETF))
                .isEqualTo(FundCategory.SECTOR);
        assertThat(FundCategoryClassifier.classify("招商中证白酒指数A", FundSubType.INDEX))
                .isEqualTo(FundCategory.SECTOR);
        assertThat(FundCategoryClassifier.classify("国泰中证全指家电ETF", FundSubType.ETF))
                .isEqualTo(FundCategory.SECTOR);
    }

    @Test
    void 行业关键词未进宽基表_判SECTOR() {
        assertThat(FundCategoryClassifier.classify("中欧医疗健康混合A", FundSubType.ACTIVE))
                .isEqualTo(FundCategory.MIXED);
        assertThat(FundCategoryClassifier.classify("广发高端制造股票A", FundSubType.INDEX))
                .isEqualTo(FundCategory.SECTOR);
    }

    @Test
    void 主动类混合_判MIXED_主动类_判ACTIVE() {
        assertThat(FundCategoryClassifier.classify("兴全合宜混合A", FundSubType.ACTIVE))
                .isEqualTo(FundCategory.MIXED);
        assertThat(FundCategoryClassifier.classify("易方达蓝筹精选混合", FundSubType.ACTIVE))
                .isEqualTo(FundCategory.MIXED);
        assertThat(FundCategoryClassifier.classify("中欧新趋势股票", FundSubType.ACTIVE))
                .isEqualTo(FundCategory.ACTIVE);
    }

    @Test
    void 指数基金无行业词_兜底BROAD_BASE() {
        assertThat(FundCategoryClassifier.classify("天弘中证全指医药卫生指数A", FundSubType.INDEX))
                .isEqualTo(FundCategory.SECTOR);
        assertThat(FundCategoryClassifier.classify("嘉实中证500指数A", FundSubType.INDEX))
                .isEqualTo(FundCategory.BROAD_BASE);
    }
}
