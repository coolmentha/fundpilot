package com.fundpilot.backend.accounting.infrastructure.gateway.transactionledger;

import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradingDayGateway;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 交易日查询适配 MarketData 的交易日历。 */
@Component
@RequiredArgsConstructor
public class TradingDayGatewayImpl implements TradingDayGateway {
    private final TradingCalendarApi calendar;

    @Override
    public Optional<Instant> latestTradingDayOnOrBefore(Instant date) {
        return calendar.latestOnOrBefore(date);
    }
}
