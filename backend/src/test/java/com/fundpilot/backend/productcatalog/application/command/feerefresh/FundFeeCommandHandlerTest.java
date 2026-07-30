package com.fundpilot.backend.productcatalog.application.command.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FundFeeCommandHandlerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void writesFetchedScheduleOutsideTheRemoteCallBoundary() {
        FundFeeScheduleRepository schedules = mock(FundFeeScheduleRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleWriter writer = mock(FundFeeScheduleWriter.class);
        var fetched = new FundFeeSourceGateway.SourceFee(null, null, null,
                List.of(new FundFeeSourceGateway.SourceRedemptionTier(7, java.math.BigDecimal.ZERO)));
        var saved = com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.create(
                "001071", null, null, null, List.of(), CLOCK.instant());
        when(source.fetch("001071")).thenReturn(fetched);
        when(writer.write("001071", fetched, CLOCK.instant())).thenReturn(saved);
        var handler = new FundFeeCommandHandler(schedules, source, writer, CLOCK);

        var result = handler.refresh("001071");

        assertThat(result).isPresent();
    }

    @Test
    void sourceFailureKeepsExistingScheduleUntouched() {
        FundFeeScheduleRepository schedules = mock(FundFeeScheduleRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleWriter writer = mock(FundFeeScheduleWriter.class);
        when(source.fetch("001071")).thenThrow(new IllegalStateException("upstream unavailable"));
        var handler = new FundFeeCommandHandler(schedules, source, writer, CLOCK);

        assertThat(handler.refresh("001071")).isEmpty();
        verifyNoInteractions(writer);
    }

    @Test
    void persistenceFailureIsNotMisreportedAsSourceDegradation() {
        FundFeeScheduleRepository schedules = mock(FundFeeScheduleRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleWriter writer = mock(FundFeeScheduleWriter.class);
        var fetched = new FundFeeSourceGateway.SourceFee(null, null, null, List.of());
        when(source.fetch("001071")).thenReturn(fetched);
        when(writer.write("001071", fetched, CLOCK.instant()))
                .thenThrow(new IllegalStateException("database unavailable"));
        var handler = new FundFeeCommandHandler(schedules, source, writer, CLOCK);

        assertThatThrownBy(() -> handler.refresh("001071"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }
}
