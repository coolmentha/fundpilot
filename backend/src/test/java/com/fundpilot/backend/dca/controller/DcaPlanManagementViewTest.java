package com.fundpilot.backend.dca.controller;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.fund.entity.FundEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DcaPlanManagementViewTest {

    @Test
    void from_按剩余日期数量计算计划剩余金额() {
        FundEntity fund = new FundEntity();
        fund.setId(3L);
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setId(7L);
        plan.setFundEntity(fund);
        plan.setEnabled(true);
        plan.setAmount(new BigDecimal("250"));
        plan.setFrequency(DcaFrequency.WEEKLY);
        plan.setDayOfWeek(3);
        plan.setStatus(DcaPlanStatus.EFFECTIVE);

        DcaPlanManagementView view = DcaPlanManagementView.from(plan, List.of(
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-22T00:00:00Z")));

        assertThat(view.fundName()).isEqualTo("沪深300ETF");
        assertThat(view.remainingOccurrences()).isEqualTo(2);
        assertThat(view.remainingAmount()).isEqualByComparingTo("500");
        assertThat(view.remainingExecutionDates()).hasSize(2);
    }
}
