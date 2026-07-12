package com.fundpilot.backend.dca.job;

import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.dca.service.DcaSuggestionService;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

class DcaSuggestionJobUnitTest {

    private static final Instant NOW = Instant.parse("2026-07-10T06:55:00Z");

    @Test
    void 单只基金失败_继续处理其他基金() {
        FundDcaPlanRepository planRepository = mock(FundDcaPlanRepository.class);
        TradingCalendarService calendarService = mock(TradingCalendarService.class);
        DcaSuggestionService suggestionService = mock(DcaSuggestionService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(calendarService.isTradingDay(any())).thenReturn(true);
        when(planRepository.findEffectiveFundIds()).thenReturn(List.of(1L, 2L));
        when(suggestionService.generateForFund(1L, NOW)).thenThrow(new RuntimeException("boom"));
        when(suggestionService.generateForFund(2L, NOW)).thenReturn(true);

        new DcaSuggestionJob(planRepository, calendarService, suggestionService, clock).run();

        verify(suggestionService).generateForFund(1L, NOW);
        verify(suggestionService).generateForFund(2L, NOW);
    }

    @Test
    void 非交易日_不查询计划也不生成() {
        FundDcaPlanRepository planRepository = mock(FundDcaPlanRepository.class);
        TradingCalendarService calendarService = mock(TradingCalendarService.class);
        DcaSuggestionService suggestionService = mock(DcaSuggestionService.class);
        when(calendarService.isTradingDay(any())).thenReturn(false);

        new DcaSuggestionJob(planRepository, calendarService, suggestionService,
                Clock.fixed(NOW, ZoneOffset.UTC)).run();

        verifyNoInteractions(planRepository, suggestionService);
    }
}
