package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeValuationQueryHandler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundIntradayControllerTest {

    @Test
    void view保留交易时段字段() {
        var result = new RealtimeValuationQueryHandler.IntradayResult("2026-07-20", new BigDecimal("1.0000"),
                List.of(new RealtimeValuationQueryHandler.IntradayPoint("09:30", new BigDecimal("1.0010"))),
                List.of(new RealtimeValuationQueryHandler.IntradaySession("09:30", "11:30"),
                        new RealtimeValuationQueryHandler.IntradaySession("13:00", "15:00")));

        var view = FundIntradayController.FundIntradayView.from(result);

        assertThat(view.tradingSessions()).containsExactly(
                new FundIntradayController.TradingSession("09:30", "11:30"),
                new FundIntradayController.TradingSession("13:00", "15:00"));
    }
}
