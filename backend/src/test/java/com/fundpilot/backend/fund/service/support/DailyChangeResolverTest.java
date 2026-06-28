package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #38 验收:三态今日涨跌判定纯函数 {@link DailyChangeResolver}。
 * <p>「今日涨跌」值随时段切换数据源(详见 PRD #34 / ADR-0008):
 * <ul>
 *   <li>盘前(北京时间 <9:30)= 0,isEstimated=false</li>
 *   <li>盘中/待公布(≥9:30 且当日净值未落库)= fundgz gszzl,isEstimated=true</li>
 *   <li>盘后(当日净值已落库)= 当日净值/昨日净值-1,isEstimated=false</li>
 * </ul>
 * 判定:当前时间 + 当日净值是否落库(非纯时间)。
 */
class DailyChangeResolverTest {

    @Test
    void 盘前_9点30前_今日涨跌为0_非估算() {
        // 北京时间 08:00(UTC 00:00)< 9:30
        Instant now = ZonedDateTime.of(2026, 6, 26, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

        DailyChangeResult result = DailyChangeResolver.resolve(now, false,
                new BigDecimal("1.10"), new BigDecimal("1.00"), Optional.empty());

        assertThat(result.todayChangePct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.isEstimated()).isFalse();
    }

    @Test
    void 盘中_当日净值未落库_用fundgz估值_isEstimated_true() {
        // 北京时间 14:00(UTC 06:00)≥ 9:30,当日净值未落库
        Instant now = ZonedDateTime.of(2026, 6, 26, 6, 0, 0, 0, ZoneOffset.UTC).toInstant();
        FundEstimateSnapshot estimate = new FundEstimateSnapshot(
                new BigDecimal("-0.0462"), "2026-06-26 14:00", "2026-06-25");

        DailyChangeResult result = DailyChangeResolver.resolve(now, false,
                new BigDecimal("1.00"), new BigDecimal("0.99"), Optional.of(estimate));

        // 用 fundgz 估算涨跌幅 -4.62%,标记估算
        assertThat(result.todayChangePct()).isEqualByComparingTo(new BigDecimal("-0.0462"));
        assertThat(result.isEstimated()).isTrue();
    }

    @Test
    void 盘后_当日净值已落库_用落库净值算_isEstimated_false() {
        // 当日净值已落库(无论时间)→ 用落库净值算
        Instant now = ZonedDateTime.of(2026, 6, 26, 14, 0, 0, 0, ZoneOffset.UTC).toInstant();
        // latest=1.10(当日), previous=1.00(昨日)→ +10%
        DailyChangeResult result = DailyChangeResolver.resolve(now, true,
                new BigDecimal("1.10"), new BigDecimal("1.00"), Optional.empty());

        assertThat(result.todayChangePct()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(result.isEstimated()).isFalse();
    }

    @Test
    void 盘中但无fundgz估值_降级用落库净值算_非估算() {
        // 盘中、当日净值未落库、但 fundgz 拉不到(空)→ 降级用落库最近两期算(T-1 vs T-2),非估算
        Instant now = ZonedDateTime.of(2026, 6, 26, 6, 0, 0, 0, ZoneOffset.UTC).toInstant();
        DailyChangeResult result = DailyChangeResolver.resolve(now, false,
                new BigDecimal("1.00"), new BigDecimal("0.99"), Optional.empty());

        // 降级:用落库净值算 (1.00-0.99)/0.99,非估算
        assertThat(result.todayChangePct()).isNotNull();
        assertThat(result.isEstimated()).isFalse();
    }
}
