package com.fundpilot.backend.alerting.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 回撤止盈状态机：照搬旧纪律策略的阶段推进与冷静期口径。 */
class SuggestionStateTest {

    private static final BigDecimal ACTIVATION = new BigDecimal("0.15");
    private static final Instant TODAY = Instant.parse("2026-09-15T00:00:00Z");
    private static final Instant TOMORROW = Instant.parse("2026-09-16T00:00:00Z");

    @Test
    void 新建状态处于累积期且无需落库() {
        SuggestionState state = SuggestionState.create(1L, 2L, 3L);

        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ACCUMULATING);
        assertThat(state.pristine()).isTrue();
        assertThat(state.id()).isNull();
        assertThat(state.cycleStartedAt()).isNull();
        assertThat(state.cyclePeakNav()).isNull();
        assertThat(state.cooldownStartedAt()).isNull();
    }

    @Test
    void 标识必须为正数() {
        assertThatThrownBy(() -> SuggestionState.create(0L, 2L, 3L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SuggestionState.create(1L, 0L, 3L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 收益率达标当次只就位不触发() {
        SuggestionState state = SuggestionState.create(1L, 2L, 3L);

        boolean triggerable = state.prepareTakeProfit(new BigDecimal("0.20"), new BigDecimal("3.0"),
                ACTIVATION, TODAY, true);

        assertThat(triggerable).isFalse();
        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(state.cycleStartedAt()).isEqualTo(TODAY);
        assertThat(state.cyclePeakNav()).isEqualByComparingTo("3.0");
        assertThat(state.pristine()).isFalse();
    }

    @Test
    void 就位后净值创新高只抬升峰值不触发() {
        SuggestionState state = armed();

        boolean triggerable = state.prepareTakeProfit(new BigDecimal("0.30"), new BigDecimal("3.6"),
                ACTIVATION, TOMORROW, true);

        assertThat(triggerable).isFalse();
        assertThat(state.cyclePeakNav()).isEqualByComparingTo("3.6");
        assertThat(state.cycleStartedAt()).isEqualTo(TODAY);
    }

    @Test
    void 就位后净值未创新高即可触发() {
        SuggestionState state = armed();

        assertThat(state.prepareTakeProfit(new BigDecimal("0.25"), new BigDecimal("2.9"),
                ACTIVATION, TOMORROW, true)).isTrue();
    }

    @Test
    void 缺少持仓数据时不推进状态() {
        SuggestionState state = armed();

        assertThat(state.prepareTakeProfit(null, new BigDecimal("2.9"), ACTIVATION, TOMORROW, true)).isFalse();
        assertThat(state.prepareTakeProfit(new BigDecimal("0.25"), null, ACTIVATION, TOMORROW, true)).isFalse();
        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ARMED);
    }

    @Test
    void 冷静期未满不再触发也不重置状态() {
        SuggestionState state = triggered();

        assertThat(state.prepareTakeProfit(new BigDecimal("0.30"), new BigDecimal("2.9"),
                ACTIVATION, TOMORROW, false)).isFalse();
        assertThat(state.phase()).isEqualTo(TakeProfitPhase.COOLDOWN);
        assertThat(state.cooldownStartedAt()).isEqualTo(TODAY);
    }

    @Test
    void 冷静期走完收益率仍达标则重新就位() {
        SuggestionState state = triggered();

        boolean triggerable = state.prepareTakeProfit(new BigDecimal("0.30"), new BigDecimal("3.2"),
                ACTIVATION, TOMORROW, true);

        assertThat(triggerable).isFalse();
        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(state.cooldownStartedAt()).isNull();
        assertThat(state.cycleStartedAt()).isEqualTo(TOMORROW);
        assertThat(state.cyclePeakNav()).isEqualByComparingTo("3.2");
    }

    @Test
    void 冷静期走完收益率不达标则回到累积期() {
        SuggestionState state = triggered();

        assertThat(state.prepareTakeProfit(new BigDecimal("0.05"), new BigDecimal("3.2"),
                ACTIVATION, TOMORROW, true)).isFalse();
        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ACCUMULATING);
        assertThat(state.pristine()).isTrue();
    }

    @Test
    void 已触发状态不再接受推进() {
        SuggestionState state = armed();
        state.markTriggered();

        assertThat(state.phase()).isEqualTo(TakeProfitPhase.TRIGGERED);
        assertThat(state.prepareTakeProfit(new BigDecimal("0.30"), new BigDecimal("2.9"),
                ACTIVATION, TOMORROW, true)).isFalse();
    }

    @Test
    void 冷静期状态不被已触发标记覆盖() {
        SuggestionState state = triggered();

        state.markTriggered();

        assertThat(state.phase()).isEqualTo(TakeProfitPhase.COOLDOWN);
    }

    @Test
    void 非触发状态进入冷静期时幂等忽略() {
        SuggestionState state = armed();

        state.enterCooldown(TODAY);

        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(state.cooldownStartedAt()).isNull();
    }

    @Test
    void 重置回到起点以便重新提醒() {
        SuggestionState state = armed();

        state.reset();

        assertThat(state.phase()).isEqualTo(TakeProfitPhase.ACCUMULATING);
        assertThat(state.pristine()).isTrue();
    }

    /** 已就位（ARMED）并记录峰值 3.0。 */
    private static SuggestionState armed() {
        SuggestionState state = SuggestionState.create(1L, 2L, 3L);
        state.prepareTakeProfit(new BigDecimal("0.20"), new BigDecimal("3.0"), ACTIVATION, TODAY, true);
        return state;
    }

    /** 已触发（TRIGGERED）并进入冷静期，冷静期起始时间为 TODAY。 */
    private static SuggestionState triggered() {
        SuggestionState state = armed();
        state.markTriggered();
        state.enterCooldown(TODAY);
        return state;
    }
}