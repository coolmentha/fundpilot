package com.fundpilot.backend.investmentplan.adapter.scheduler.planexecution;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.command.planexecution.InvestmentPlanExecutionCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentPlanExecutionJobTest {
    @Test
    void schedulerExecutesCurrentPlanIds() {
        var queries = mock(InvestmentPlanQueryHandler.class);
        var commands = mock(InvestmentPlanExecutionCommandHandler.class);
        var now = Instant.parse("2026-09-07T06:55:00Z");
        when(queries.effectiveEnabledIds()).thenReturn(List.of(7L));

        new InvestmentPlanExecutionJob(queries, commands, Clock.fixed(now, ZoneOffset.UTC)).run();

        verify(commands).execute(7L, now);
    }
}
