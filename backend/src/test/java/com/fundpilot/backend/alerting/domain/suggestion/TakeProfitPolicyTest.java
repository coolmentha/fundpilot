package com.fundpilot.backend.alerting.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 建议份额与回撤口径的纯计算；取值与旧 AdvicePolicy 逐行一致。 */
class TakeProfitPolicyTest {

    private static final TakeProfitParams PARAMS = new TakeProfitParams(new BigDecimal("0.15"),
            new BigDecimal("0.06"), new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("0.20"), 10);

    @Test
    void 建议份额取四者最小值() {
        // 浮盈 5000 × 收割 0.5 ÷ 1.5 = 1666.67；份额 10000 × 单次上限 0.2 = 2000；
        // 份额 × (1 − 最低保留 0.5) = 5000；成熟可赎回 10000 → 取 1666.67
        BigDecimal shares = TakeProfitPolicy.suggestedShares(new BigDecimal("5000"), new BigDecimal("10000"),
                new BigDecimal("1.5"), PARAMS, new BigDecimal("10000"));

        assertThat(shares).isEqualByComparingTo("1666.666666666667");
    }

    @Test
    void 单次上限成为瓶颈时按上限卖出() {
        BigDecimal shares = TakeProfitPolicy.suggestedShares(new BigDecimal("100000"), new BigDecimal("10000"),
                new BigDecimal("1.5"), PARAMS, new BigDecimal("10000"));

        assertThat(shares).isEqualByComparingTo("2000");
    }

    @Test
    void 最低保留成为瓶颈时按保留比例封顶() {
        TakeProfitParams keepMost = new TakeProfitParams(new BigDecimal("0.15"), new BigDecimal("0.06"),
                new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.90"), 10);

        BigDecimal shares = TakeProfitPolicy.suggestedShares(new BigDecimal("100000"), new BigDecimal("10000"),
                new BigDecimal("1.5"), keepMost, new BigDecimal("10000"));

        assertThat(shares).isEqualByComparingTo("1000");
    }

    @Test
    void 成熟可赎回份额成为瓶颈时按可赎回份额卖出() {
        BigDecimal shares = TakeProfitPolicy.suggestedShares(new BigDecimal("100000"), new BigDecimal("10000"),
                new BigDecimal("1.5"), PARAMS, new BigDecimal("300"));

        assertThat(shares).isEqualByComparingTo("300");
    }

    @Test
    void 四者都为非正时建议份额为零() {
        BigDecimal shares = TakeProfitPolicy.suggestedShares(BigDecimal.ZERO, new BigDecimal("10000"),
                new BigDecimal("1.5"), PARAMS, BigDecimal.ZERO);

        assertThat(shares).isEqualByComparingTo("0");
    }

    @Test
    void 浮盈下限为零() {
        assertThat(TakeProfitPolicy.floatingProfit(new BigDecimal("1.5"), new BigDecimal("10000"),
                new BigDecimal("1.0"))).isEqualByComparingTo("0");
        assertThat(TakeProfitPolicy.floatingProfit(new BigDecimal("1.0"), new BigDecimal("10000"),
                new BigDecimal("1.5"))).isEqualByComparingTo("5000");
    }

    @Test
    void 总体收益率与回撤口径() {
        assertThat(TakeProfitPolicy.overallReturn(new BigDecimal("5000"), new BigDecimal("10000")))
                .isEqualByComparingTo("0.5");
        assertThat(TakeProfitPolicy.overallReturn(new BigDecimal("5000"), BigDecimal.ZERO)).isNull();
        assertThat(TakeProfitPolicy.pullback(new BigDecimal("3.0"), new BigDecimal("2.4")))
                .isEqualByComparingTo("0.2");
        assertThat(TakeProfitPolicy.pullback(null, new BigDecimal("2.8"))).isNull();
        assertThat(TakeProfitPolicy.pullback(new BigDecimal("3.0"), null)).isNull();
    }

    @Test
    void 正数判定覆盖空值与非正() {
        assertThat(TakeProfitPolicy.positive(new BigDecimal("0.01"))).isTrue();
        assertThat(TakeProfitPolicy.positive(BigDecimal.ZERO)).isFalse();
        assertThat(TakeProfitPolicy.positive(new BigDecimal("-1"))).isFalse();
        assertThat(TakeProfitPolicy.positive(null)).isFalse();
    }
}