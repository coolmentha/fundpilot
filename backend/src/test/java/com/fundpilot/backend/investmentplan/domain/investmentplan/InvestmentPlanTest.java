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

    @Test
    void 月定投计划日跨月顺延_月末连续休市后跨月首日补执行() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 28, InvestmentPlanStatus.EFFECTIVE);

        assertThat(plan.executableOn(Instant.parse("2026-03-02T00:00:00Z"), false,
                Instant.parse("2026-02-27T00:00:00Z"))).isTrue();
        assertThat(plan.executableOn(Instant.parse("2026-03-03T00:00:00Z"), true,
                Instant.parse("2026-03-02T00:00:00Z"))).isFalse();
    }

    @Test
    void 月定投跨月首日之前仍有交易日_不补执行() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 28, InvestmentPlanStatus.EFFECTIVE);

        assertThat(plan.executableOn(Instant.parse("2026-03-02T00:00:00Z"), false,
                Instant.parse("2026-02-28T00:00:00Z"))).isFalse();
    }

    @Test
    void 跨月补跑不追溯创建之前的月份() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 28, InvestmentPlanStatus.EFFECTIVE,
                Instant.parse("2026-03-02T02:00:00Z"));

        assertThat(plan.executableOn(Instant.parse("2026-03-02T00:00:00Z"), false,
                Instant.parse("2026-02-27T00:00:00Z"))).isFalse();
        assertThat(plan.executableOn(Instant.parse("2026-03-28T00:00:00Z"), false,
                Instant.parse("2026-03-27T00:00:00Z"))).isTrue();
    }

    @Test
    void 新建月定投创建日已过计划日_当月不执行() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 1, InvestmentPlanStatus.EFFECTIVE,
                Instant.parse("2026-08-15T02:00:00Z"));

        assertThat(plan.executableOn(Instant.parse("2026-08-15T00:00:00Z"), false)).isFalse();
        assertThat(plan.executableOn(Instant.parse("2026-08-20T00:00:00Z"), false)).isFalse();
        assertThat(plan.executableOn(Instant.parse("2026-09-01T00:00:00Z"), false)).isTrue();
    }

    @Test
    void 新建月定投创建日未过计划日_计划日当天可执行() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 20, InvestmentPlanStatus.EFFECTIVE,
                Instant.parse("2026-08-15T02:00:00Z"));

        assertThat(plan.executableOn(Instant.parse("2026-08-15T00:00:00Z"), false)).isFalse();
        assertThat(plan.executableOn(Instant.parse("2026-08-20T00:00:00Z"), false)).isTrue();
    }

    @Test
    void 新建月定投创建日等于计划日_当月不执行() {
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 15, InvestmentPlanStatus.EFFECTIVE,
                Instant.parse("2026-08-15T02:00:00Z"));

        assertThat(plan.executableOn(Instant.parse("2026-08-15T00:00:00Z"), false)).isFalse();
        assertThat(plan.executableOn(Instant.parse("2026-08-20T00:00:00Z"), false)).isFalse();
        assertThat(plan.executableOn(Instant.parse("2026-09-15T00:00:00Z"), false)).isTrue();
    }
}
