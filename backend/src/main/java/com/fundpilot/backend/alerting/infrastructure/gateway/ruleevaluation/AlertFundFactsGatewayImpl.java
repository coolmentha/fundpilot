package com.fundpilot.backend.alerting.infrastructure.gateway.ruleevaluation;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.insights.adapter.api.fundreturn.FundReturnApi;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 组合 insights 的持仓收益快照与 marketdata 的交易日历。 */
@Component
@RequiredArgsConstructor
public class AlertFundFactsGatewayImpl implements AlertFundFactsGateway {

    private final FundReturnApi fundReturns;
    private final TradingCalendarApi tradingCalendar;

    @Override
    public List<AlertFundFact> currentFunds(long ownerId) {
        return fundReturns.currentFunds(ownerId).stream()
                .map(snapshot -> new AlertFundFact(snapshot.portfolioFundId(), snapshot.fundCode(),
                        snapshot.fundName(), snapshot.positionStatus(), snapshot.open(),
                        snapshot.dailyChangePct(), snapshot.holdingAmount(), snapshot.unrealizedPnl(),
                        snapshot.holdingReturnRate(), snapshot.valuationNav(), snapshot.valuationDate(),
                        snapshot.estimateStatus()))
                .toList();
    }

    @Override
    public boolean isTradingDay(Instant at) {
        return tradingCalendar.isTradingDay(at);
    }
}
