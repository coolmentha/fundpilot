package com.fundpilot.backend.discipline.domain.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.TakeProfitPhase;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdvicePolicyTest {
    private static final Instant TODAY = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void active逻辑破位优先清仓并记录持有期豁免() {
        var result = new AdvicePolicy().evaluate(strategy(), facts(AdvicePolicy.ProductType.ACTIVE, AdvicePolicy.PositionStatus.OPEN, "1", "100",
                false, AdvicePolicy.MacdState.GREEN_EXPANDING, AdvicePolicy.VolumeState.NORMAL, "1", "1", "100"), 2, true);

        assertThat(result.action()).isEqualTo(AdviceAction.SELL);
        assertThat(result.suggestedValue()).isEqualByComparingTo("100");
        assertThat(result.reason()).isEqualTo("LOGIC_BROKEN");
        assertThat(result.warnings()).containsExactly("MIN_HOLD_DAYS_OVERRIDDEN");
    }

    @Test
    void 年线数据不足时不误判逻辑破位清仓() {
        var result = new AdvicePolicy().evaluate(strategy(), facts(AdvicePolicy.ProductType.ACTIVE, AdvicePolicy.PositionStatus.OPEN, "1", "100",
                null, AdvicePolicy.MacdState.GREEN_EXPANDING, AdvicePolicy.VolumeState.NORMAL, "1", "1", "100"), 10, true);

        assertThat(result.action()).isEqualTo(AdviceAction.NONE);
        assertThat(result.reason()).isEqualTo("NO_SELL_TRIGGER");
    }

    @Test
    void 移动止盈先建立峰值再按回撤与限额计算份额() {
        DisciplineStrategy strategy = strategy();
        AdvicePolicy policy = new AdvicePolicy();
        var armed = policy.evaluate(strategy, facts(AdvicePolicy.ProductType.ETF, AdvicePolicy.PositionStatus.OPEN, "1", "100",
                true, AdvicePolicy.MacdState.RED_SHRINKING, AdvicePolicy.VolumeState.NORMAL, "2", "2", "100"), 10, true);
        var triggered = policy.evaluate(strategy, facts(AdvicePolicy.ProductType.ETF, AdvicePolicy.PositionStatus.OPEN, "1", "100",
                true, AdvicePolicy.MacdState.RED_SHRINKING, AdvicePolicy.VolumeState.NORMAL, "2", "1.7", "100"), 10, true);

        assertThat(armed.action()).isEqualTo(AdviceAction.NONE);
        assertThat(strategy.takeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(triggered.action()).isEqualTo(AdviceAction.SELL);
        assertThat(triggered.reason()).isEqualTo("TRAILING_STOP");
        assertThat(triggered.suggestedValue()).isEqualByComparingTo("25");
    }

    private static DisciplineStrategy strategy() {
        DisciplineStrategy strategy = DisciplineStrategy.create(1L, 2L,
                new DisciplineStrategy.Input(new BigDecimal("0.20"), new BigDecimal("0.10"),
                        new BigDecimal("0.50"), new BigDecimal("0.20"),
                        new BigDecimal("0.50"), 5));
        strategy.activate();
        return strategy;
    }

    private static AdvicePolicy.Facts facts(AdvicePolicy.ProductType type, AdvicePolicy.PositionStatus status,
                                             String cost, String shares,
                                             Boolean aboveYearLine, AdvicePolicy.MacdState macd,
                                             AdvicePolicy.VolumeState volume,
                                             String unitNav, String accumulatedNav, String matureShares) {
        return new AdvicePolicy.Facts(type, status, new BigDecimal(cost), new BigDecimal(shares),
                new AdvicePolicy.Market(aboveYearLine, macd, volume), new BigDecimal(unitNav),
                new BigDecimal(accumulatedNav), new BigDecimal(matureShares), TODAY);
    }
}
