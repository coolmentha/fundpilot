package com.fundpilot.backend.marketdata.application.command.tradingcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.gateway.tradingcalendar.TradingCalendarSourceGateway;
import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingCalendarRepository;
import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingDay;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingCalendarCommandHandlerTest {
    private static final Instant FIRST = Instant.parse("2026-07-28T00:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-07-29T00:00:00Z");

    @Mock TradingCalendarRepository calendar;
    @Mock TradingCalendarSourceGateway source;
    @InjectMocks TradingCalendarCommandHandler handler;

    @Test
    void incrementalOnlyPersistsDatesAfterCurrentMaximum() {
        when(source.fetchTradingDays()).thenReturn(List.of(FIRST, SECOND));
        when(calendar.maxDate()).thenReturn(Optional.of(FIRST));
        when(calendar.addIfAbsent(List.of(new TradingDay(SECOND)))).thenReturn(1);

        assertThat(handler.synchronize(true)).isEqualTo(1);
    }

    @Test
    void fullSynchronizationDoesNotReadCurrentMaximum() {
        List<TradingDay> expected = List.of(new TradingDay(FIRST), new TradingDay(SECOND));
        when(source.fetchTradingDays()).thenReturn(List.of(FIRST, SECOND));
        when(calendar.addIfAbsent(expected)).thenReturn(2);

        assertThat(handler.synchronize(false)).isEqualTo(2);
        verify(calendar).addIfAbsent(expected);
    }
}
