package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 赎回费档位解析单测(issue #60):验证从东方财富详情页文本解析赎回费档位表。
 * <p>纯函数,输入详情页片段文本,输出档位列表(持有期升序)。
 */
class RedemptionFeeParserTest {

    /** 典型档位文本:<7日1.5%、<30日0.75%、<365日0.5%、≥365日0%。 */
    @Test
    void 解析典型赎回费档位文本_返回升序档位表() {
        String text = "赎回费率:持有少于7日1.50%,持有不少于7日少于30日0.75%,"
                + "持有不少于30日少于365日0.50%,持有不少于365日0.00%";

        List<RedemptionFeeTier> tiers = RedemptionFeeParser.parse(text);

        assertThat(tiers).hasSize(4);
        assertThat(tiers.get(0).holdingDays()).isEqualTo(1);
        assertThat(tiers.get(0).feeRate()).isEqualByComparingTo("0.015");
        assertThat(tiers.get(1).holdingDays()).isEqualTo(7);
        assertThat(tiers.get(1).feeRate()).isEqualByComparingTo("0.0075");
        assertThat(tiers.get(2).holdingDays()).isEqualTo(30);
        assertThat(tiers.get(2).feeRate()).isEqualByComparingTo("0.005");
        assertThat(tiers.get(3).holdingDays()).isEqualTo(365);
        assertThat(tiers.get(3).feeRate()).isEqualByComparingTo("0");
    }

    /** 空文本或无匹配 → 返空列表(数据缺失由调用方处理)。 */
    @Test
    void 无匹配文本_返回空列表() {
        assertThat(RedemptionFeeParser.parse("")).isEmpty();
        assertThat(RedemptionFeeParser.parse("该基金无赎回费信息")).isEmpty();
    }

    /** 单档文本(如货币基金 0 赎回费)。 */
    @Test
    void 单档文本_返回单元素列表() {
        String text = "赎回费率:0.00%";

        List<RedemptionFeeTier> tiers = RedemptionFeeParser.parse(text);

        assertThat(tiers).hasSize(1);
        assertThat(tiers.get(0).holdingDays()).isEqualTo(1);
        assertThat(tiers.get(0).feeRate()).isEqualByComparingTo("0");
    }
}