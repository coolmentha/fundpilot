package com.fundpilot.backend.investmentplan.domain.investmentplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvestmentPlanTest {
    @Test
    void 月计划在非交易日后只命中首个交易日() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 2, InvestmentPlanStatus.EFFECTIVE);

        assertThat(plan.executableOn(Instant.parse("2026-08-03T00:00:00Z"), false)).isTrue();
        assertThat(plan.executableOn(Instant.parse("2026-08-04T00:00:00Z"), true)).isFalse();
    }

    @Test
    void 月计划计划日当天Job未跑次日起仍可补跑一次() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 2, InvestmentPlanStatus.EFFECTIVE);

        assertThat(plan.executableOn(Instant.parse("2026-08-03T00:00:00Z"), false)).isTrue();
        assertThat(plan.executableOn(Instant.parse("2026-08-04T00:00:00Z"), false)).isTrue();
        assertThat(plan.executableOn(Instant.parse("2026-08-05T00:00:00Z"), true)).isFalse();
    }

    @Test
    void 生效计划可以更新尚未执行的参数() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.WEEKLY, 1, null, InvestmentPlanStatus.EFFECTIVE);

        plan.update(false, new BigDecimal("200"), InvestmentPlanFrequency.MONTHLY, null, 15);

        assertThat(plan.enabled()).isFalse();
        assertThat(plan.amount()).isEqualByComparingTo("200");
        assertThat(plan.frequency()).isEqualTo(InvestmentPlanFrequency.MONTHLY);
        assertThat(plan.dayOfWeek()).isNull();
        assertThat(plan.dayOfMonth()).isEqualTo(15);
    }

    @Test
    void 组合基金作废后计划保留但不可再执行() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.DAILY, null, null, InvestmentPlanStatus.EFFECTIVE);

        plan.disableForVoidedPortfolioFund();

        assertThat(plan.id()).isEqualTo(7L);
        assertThat(plan.enabled()).isFalse();
        assertThat(plan.status()).isEqualTo(InvestmentPlanStatus.DRAFT);
        assertThat(plan.executableOn(Instant.parse("2026-07-29T00:00:00Z"), false)).isFalse();
    }
}
