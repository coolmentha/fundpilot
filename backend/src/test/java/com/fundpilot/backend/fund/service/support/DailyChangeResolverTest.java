package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.service.EstimateStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DailyChangeResolverTest {

    @Test
    void 当日净值已确认_始终使用实际涨跌() {
        DailyChangeResult result = DailyChangeResolver.resolve(true,
                new BigDecimal("1.10"), new BigDecimal("1.00"), Optional.empty(), EstimateStatus.TIMEOUT);

        assertThat(result.todayChangePct()).isEqualByComparingTo("0.10");
        assertThat(result.isEstimated()).isFalse();
    }

    @Test
    void 当日估值可用_使用估算涨跌() {
        FundEstimateSnapshot estimate = new FundEstimateSnapshot(
                new BigDecimal("-0.0462"), "2026-07-16 22:29", "2026-07-15");

        DailyChangeResult result = DailyChangeResolver.resolve(false,
                new BigDecimal("1.00"), new BigDecimal("0.99"), Optional.of(estimate), EstimateStatus.AVAILABLE);

        assertThat(result.todayChangePct()).isEqualByComparingTo("-0.0462");
        assertThat(result.isEstimated()).isTrue();
    }

    @Test
    void 当日估值尚未出现_今日涨跌为0() {
        for (EstimateStatus status : new EstimateStatus[]{EstimateStatus.STALE, EstimateStatus.NOT_ATTEMPTED}) {
            DailyChangeResult result = DailyChangeResolver.resolve(false,
                    new BigDecimal("1.00"), new BigDecimal("0.99"), Optional.empty(), status);

            assertThat(result.todayChangePct()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.isEstimated()).isFalse();
        }
    }

    @Test
    void 估值空响应或失败_今日数据未知() {
        for (EstimateStatus status : new EstimateStatus[]{
                EstimateStatus.UNAVAILABLE, EstimateStatus.TIMEOUT, EstimateStatus.PARSE_ERROR}) {
            DailyChangeResult result = DailyChangeResolver.resolve(false,
                    new BigDecimal("1.00"), new BigDecimal("0.99"), Optional.empty(), status);

            assertThat(result.todayChangePct()).isNull();
            assertThat(result.isEstimated()).isFalse();
        }
    }
}
