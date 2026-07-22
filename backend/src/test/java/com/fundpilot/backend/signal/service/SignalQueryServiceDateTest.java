package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.fund.service.FundAccessService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

class SignalQueryServiceDateTest {

    @Test
    void today_北京时间凌晨查询北京时间当天区间() {
        SignalLogRepository repository = mock(SignalLogRepository.class);
        FundTransactionRepository transactionRepository = mock(FundTransactionRepository.class);
        SignalActionabilityService actionabilityService = mock(SignalActionabilityService.class);
        TradingCalendarService calendarService = mock(TradingCalendarService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        FundAccessService fundAccessService = mock(FundAccessService.class);
        when(repository.findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(
                1L, Instant.parse("2026-07-07T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z")))
                .thenReturn(List.of());
        SignalQueryService service = new SignalQueryService(fundAccessService, repository, transactionRepository,
                actionabilityService, calendarService, clock);

        service.today(1L);

        verify(repository).findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(
                1L, Instant.parse("2026-07-07T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z"));
    }

    @Test
    void pending_不返回其他用户基金的信号() {
        SignalLogRepository repository = mock(SignalLogRepository.class);
        FundTransactionRepository transactionRepository = mock(FundTransactionRepository.class);
        SignalActionabilityService actionabilityService = mock(SignalActionabilityService.class);
        TradingCalendarService calendarService = mock(TradingCalendarService.class);
        FundAccessService fundAccessService = mock(FundAccessService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-07T08:00:00Z"), ZoneOffset.UTC);
        FundEntity otherFund = new FundEntity();
        SignalLogEntity otherSignal = new SignalLogEntity();
        otherSignal.setId(9L);
        otherSignal.setFundEntity(otherFund);
        when(repository.findRecentPendingSignals(any(), any(), any())).thenReturn(List.of(otherSignal));
        when(repository.findTriggeredPendingSignals(any(), any())).thenReturn(List.of());
        when(fundAccessService.isOwned(otherFund)).thenReturn(false);
        SignalQueryService service = new SignalQueryService(fundAccessService, repository, transactionRepository,
                actionabilityService, calendarService, clock);

        assertThat(service.pending()).isEmpty();
        verify(actionabilityService, org.mockito.Mockito.never()).isActionable(otherSignal);
    }
}
