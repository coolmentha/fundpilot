package com.fundpilot.backend.marketdata.application.command.tradingcalendar;

import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingCalendarRepository;
import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingDay;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradingCalendarCommandHandler {
    private final TradingCalendarRepository calendar;

    @Transactional
    public int addTradingDays(List<Instant> dates) {
        return calendar.addIfAbsent(dates.stream().distinct().sorted().map(TradingDay::new).toList());
    }
}
