package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler;
import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketIndicatorRefreshCommandHandlerDateTest {
    @Test
    void refreshOne_估值已覆盖截至日时不重复请求远程历史() {
        var navSource = mock(PublishedNavSourceGateway.class);
        var valuationSource = mock(PublishedIndexValuationSourceGateway.class);
        var valuationCommands = mock(IndexValuationCommandHandler.class);
        var valuationQueries = mock(IndexValuationQueryHandler.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        MarketIndicatorRefreshCommandHandler handler = new MarketIndicatorRefreshCommandHandler(
                mock(TrackedNavProductGateway.class), navSource, mock(PublishedIndexKlineSourceGateway.class),
                mock(NavPublishingCommandHandler.class), mock(com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler.class),
                mock(IndexKlineQueryHandler.class), clock, valuationSource, valuationCommands, valuationQueries);
        when(navSource.fetchHistory("510300")).thenReturn(List.of(
                nav("2026-07-03T00:00:00Z", "1.00"), nav("2026-07-06T00:00:00Z", "1.01")));
        when(valuationQueries.latest("000300", "CSINDEX_INDEX_CSI_DS_PE_PEG"))
                .thenReturn(Optional.of(new IndexValuationQueryHandler.Valuation("000300",
                        Instant.parse("2026-07-06T00:00:00Z"), new BigDecimal("12.45"),
                        "CSINDEX_INDEX_CSI_DS_PE_PEG")));

        handler.refreshOne(new MarketIndicatorRefreshCommandHandler.RefreshTarget(1L, 11L, "510300",
                "测试基金", "000300", null));

        verifyNoInteractions(valuationSource, valuationCommands);
    }

    private static PublishedNavSourceGateway.NavSnapshot nav(String date, String value) {
        BigDecimal nav = new BigDecimal(value);
        return new PublishedNavSourceGateway.NavSnapshot(Instant.parse(date), nav, nav);
    }
}
