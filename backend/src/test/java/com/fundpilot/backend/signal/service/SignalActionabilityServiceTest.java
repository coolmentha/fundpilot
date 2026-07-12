package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalActionStatus;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignalActionabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T02:00:00Z");

    @Test
    void 周末_最近周五信号仍可操作() {
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.latestTradingDayBefore(Instant.parse("2026-07-12T00:00:00Z")))
                .thenReturn(Optional.of(Instant.parse("2026-07-10T00:00:00Z")));
        SignalActionabilityService service = new SignalActionabilityService(
                calendar, Clock.fixed(NOW, ZoneOffset.UTC));
        SignalLogEntity signal = signal(1L, "2026-07-10T00:00:00Z");

        assertThat(service.isActionable(signal)).isTrue();
    }

    @Test
    void 周一当日信号生成前_上周五信号仍可操作() {
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.latestTradingDayBefore(Instant.parse("2026-07-13T00:00:00Z")))
                .thenReturn(Optional.of(Instant.parse("2026-07-10T00:00:00Z")));
        SignalActionabilityService service = new SignalActionabilityService(
                calendar, Clock.fixed(Instant.parse("2026-07-13T02:00:00Z"), ZoneOffset.UTC));
        SignalLogEntity signal = signal(1L, "2026-07-10T00:00:00Z");

        assertThat(service.isActionable(signal)).isTrue();
    }

    @Test
    void 普通旧信号已过期_绑定TRIGGERED止盈例外() {
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.latestTradingDayBefore(Instant.parse("2026-07-12T00:00:00Z")))
                .thenReturn(Optional.of(Instant.parse("2026-07-10T00:00:00Z")));
        SignalActionabilityService service = new SignalActionabilityService(
                calendar, Clock.fixed(NOW, ZoneOffset.UTC));
        SignalLogEntity signal = signal(7L, "2026-07-09T00:00:00Z");

        assertThat(service.isActionable(signal)).isFalse();

        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setTakeProfitPhase(TakeProfitPhase.TRIGGERED);
        strategy.setTriggeredSignalId(7L);
        signal.setReason(SignalReason.TRAILING_STOP);
        signal.setFundStrategyEntity(strategy);
        assertThat(service.isActionable(signal)).isTrue();
    }

    @Test
    void status_按信息已回应已忽略待回应和过期映射() {
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.latestTradingDayBefore(Instant.parse("2026-07-12T00:00:00Z")))
                .thenReturn(Optional.of(Instant.parse("2026-07-10T00:00:00Z")));
        SignalActionabilityService service = new SignalActionabilityService(
                calendar, Clock.fixed(NOW, ZoneOffset.UTC));

        SignalLogEntity informational = signal(1L, "2026-07-10T00:00:00Z");
        informational.setSignalType(SignalType.NONE);
        SignalLogEntity responded = signal(2L, "2026-07-10T00:00:00Z");
        SignalLogEntity ignored = signal(3L, "2026-07-10T00:00:00Z");
        ignored.setIgnoredDate(NOW);
        SignalLogEntity pending = signal(4L, "2026-07-10T00:00:00Z");
        SignalLogEntity expired = signal(5L, "2026-07-09T00:00:00Z");

        assertThat(service.status(informational, Set.of())).isEqualTo(SignalActionStatus.INFORMATIONAL);
        assertThat(service.status(responded, Set.of(2L))).isEqualTo(SignalActionStatus.RESPONDED);
        assertThat(service.status(ignored, Set.of())).isEqualTo(SignalActionStatus.IGNORED);
        assertThat(service.status(pending, Set.of())).isEqualTo(SignalActionStatus.PENDING);
        assertThat(service.status(expired, Set.of())).isEqualTo(SignalActionStatus.EXPIRED);
    }

    private static SignalLogEntity signal(Long id, String date) {
        SignalLogEntity signal = new SignalLogEntity();
        signal.setId(id);
        signal.setSignalType(SignalType.SELL);
        signal.setReason(SignalReason.LOGIC_BROKEN);
        signal.setSignalDate(Instant.parse(date));
        return signal;
    }
}
