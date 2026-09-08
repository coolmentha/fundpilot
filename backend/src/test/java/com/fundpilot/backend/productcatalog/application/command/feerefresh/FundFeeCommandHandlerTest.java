package com.fundpilot.backend.productcatalog.application.command.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.RefreshStatus;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import com.fundpilot.backend.productcatalog.domain.fee.RedemptionFeeTier;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundFeeCommandHandlerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void trackedFundGetsItsFirstFeeScheduleInTheBackgroundBatch() {
        FundResearchRepository research = mock(FundResearchRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleRepository schedules = mock(FundFeeScheduleRepository.class);
        var fetched = sourceFee();
        when(research.findTrackedFundCodes(100)).thenReturn(List.of("001071"));
        when(source.fetch("001071")).thenReturn(fetched);
        when(schedules.findByFundCode("001071")).thenReturn(Optional.empty());
        when(schedules.save(any(FundFeeSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new FundFeeCommandHandler(research, source,
                new FundFeeScheduleWriter(schedules), CLOCK);

        assertThat(handler.refreshTrackedFunds()).isEqualTo(1);

        ArgumentCaptor<FundFeeSchedule> saved = ArgumentCaptor.captor();
        verify(schedules).save(saved.capture());
        assertThat(saved.getValue().fundCode()).isEqualTo("001071");
        assertThat(saved.getValue().purchaseRate()).isEqualByComparingTo("0.15");
        assertThat(saved.getValue().redemptionTiers()).containsExactly(
                new RedemptionFeeTier(7, new BigDecimal("1.50")));
        assertThat(saved.getValue().refreshStatus()).isEqualTo(RefreshStatus.SUCCESS);
        assertThat(saved.getValue().fetchedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void writesFetchedScheduleOutsideTheRemoteCallBoundary() {
        FundResearchRepository research = mock(FundResearchRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleWriter writer = mock(FundFeeScheduleWriter.class);
        var fetched = new FundFeeSourceGateway.SourceFee(null, null, null,
                List.of(new FundFeeSourceGateway.SourceRedemptionTier(7, java.math.BigDecimal.ZERO)),
                null, null, null, null, null, null, null, null);
        var saved = com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.create(
                "001071", null, null, null, List.of(), CLOCK.instant());
        when(source.fetch("001071")).thenReturn(fetched);
        when(writer.write("001071", fetched, CLOCK.instant())).thenReturn(saved);
        var handler = new FundFeeCommandHandler(research, source, writer, CLOCK);

        var result = handler.refresh("001071");

        assertThat(result).isPresent();
    }

    @Test
    void sourceFailureMarksExistingScheduleFailedWithoutClearingItsValues() {
        FundResearchRepository research = mock(FundResearchRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleRepository schedules = mock(FundFeeScheduleRepository.class);
        FundFeeSchedule existing = FundFeeSchedule.create("001071", new BigDecimal("0.15"),
                new BigDecimal("0.10"), null,
                List.of(new RedemptionFeeTier(7, new BigDecimal("1.50"))), CLOCK.instant());
        when(source.fetch("001071")).thenThrow(new IllegalStateException("upstream unavailable"));
        when(schedules.findByFundCode("001071")).thenReturn(Optional.of(existing));
        when(schedules.save(existing)).thenReturn(existing);
        var handler = new FundFeeCommandHandler(research, source,
                new FundFeeScheduleWriter(schedules), CLOCK);

        assertThat(handler.refresh("001071")).isEmpty();
        assertThat(existing.refreshStatus()).isEqualTo(RefreshStatus.FAILED);
        assertThat(existing.purchaseRate()).isEqualByComparingTo("0.15");
        assertThat(existing.discountRate()).isEqualByComparingTo("0.10");
        assertThat(existing.redemptionTiers()).containsExactly(
                new RedemptionFeeTier(7, new BigDecimal("1.50")));
        assertThat(existing.fetchedAt()).isEqualTo(CLOCK.instant());
        verify(schedules).save(existing);
    }

    @Test
    void emptyParsedPageAlsoKeepsExistingScheduleValues() {
        FundResearchRepository research = mock(FundResearchRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleRepository schedules = mock(FundFeeScheduleRepository.class);
        FundFeeSchedule existing = FundFeeSchedule.create("001071", new BigDecimal("0.15"),
                null, null, List.of(), CLOCK.instant());
        when(source.fetch("001071")).thenReturn(null);
        when(schedules.findByFundCode("001071")).thenReturn(Optional.of(existing));
        when(schedules.save(existing)).thenReturn(existing);
        var handler = new FundFeeCommandHandler(research, source,
                new FundFeeScheduleWriter(schedules), CLOCK);

        assertThat(handler.refresh("001071")).isEmpty();
        assertThat(existing.purchaseRate()).isEqualByComparingTo("0.15");
        assertThat(existing.refreshStatus()).isEqualTo(RefreshStatus.FAILED);
    }

    @Test
    void persistenceFailureIsNotMisreportedAsSourceDegradation() {
        FundResearchRepository research = mock(FundResearchRepository.class);
        FundFeeSourceGateway source = mock(FundFeeSourceGateway.class);
        FundFeeScheduleWriter writer = mock(FundFeeScheduleWriter.class);
        var fetched = new FundFeeSourceGateway.SourceFee(null, null, null, List.of(),
                null, null, null, null, null, null, null, null);
        when(source.fetch("001071")).thenReturn(fetched);
        when(writer.write("001071", fetched, CLOCK.instant()))
                .thenThrow(new IllegalStateException("database unavailable"));
        var handler = new FundFeeCommandHandler(research, source, writer, CLOCK);

        assertThatThrownBy(() -> handler.refresh("001071"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private static FundFeeSourceGateway.SourceFee sourceFee() {
        return new FundFeeSourceGateway.SourceFee(new BigDecimal("0.15"), new BigDecimal("0.10"), null,
                List.of(new FundFeeSourceGateway.SourceRedemptionTier(7, new BigDecimal("1.50"))),
                null, null, null, null, null, null, "东方财富", "https://fundf10.eastmoney.com");
    }
}
