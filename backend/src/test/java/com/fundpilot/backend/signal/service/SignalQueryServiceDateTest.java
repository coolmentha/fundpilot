package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignalQueryServiceDateTest {

    @Test
    void today_北京时间凌晨查询北京时间当天区间() {
        SignalLogRepository repository = mock(SignalLogRepository.class);
        FundTransactionRepository transactionRepository = mock(FundTransactionRepository.class);
        SignalActionabilityService actionabilityService = mock(SignalActionabilityService.class);
        TradingCalendarService calendarService = mock(TradingCalendarService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        when(repository.findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(
                1L, Instant.parse("2026-07-07T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z")))
                .thenReturn(List.of());
        SignalQueryService service = new SignalQueryService(repository, transactionRepository,
                actionabilityService, calendarService, clock);

        service.today(1L);

        verify(repository).findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(
                1L, Instant.parse("2026-07-07T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z"));
    }
}
