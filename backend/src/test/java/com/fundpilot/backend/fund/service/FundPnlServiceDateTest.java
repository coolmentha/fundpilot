package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundPnlServiceDateTest {

    @Mock FundPositionService fundPositionService;
    @Mock FundNavHistoryRepository fundNavHistoryRepository;
    @Mock FundRepository fundRepository;
    @Mock MarketRealtimeCache marketRealtimeCache;

    @Test
    void 北京时间凌晨不把昨日净值认作今日已确认() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository, marketRealtimeCache, clock);
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");

        FundNavHistoryEntity yesterday = nav("2026-07-06T00:00:00Z", "1.20");
        FundNavHistoryEntity previous = nav("2026-07-03T00:00:00Z", "1.10");
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(yesterday, previous));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of(
                "510300", new FundEstimateSnapshot(new BigDecimal("0.01"),
                        "2026-07-07 00:30", "2026-07-06")));

        FundPnlService.Pnl pnl = service.computeForFund(fund);

        verify(marketRealtimeCache).getEstimates(List.of("510300"));
        assertThat(pnl.isEstimated()).isFalse();
        assertThat(pnl.dailyChangePct()).isZero();
    }

    private static FundNavHistoryEntity nav(String date, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setNavDate(Instant.parse(date));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        return entity;
    }
}
