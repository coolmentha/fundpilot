package com.fundpilot.backend.insights.infrastructure.migration.returnsnapshotrebuild;

import com.fundpilot.backend.insights.application.command.portfolioreturn.PortfolioReturnSnapshotCommandHandler;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioReturnSnapshotRebuildServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PortfolioReturnSnapshotCommandHandler snapshots = mock(PortfolioReturnSnapshotCommandHandler.class);
    private final PortfolioReturnSnapshotRebuildService service =
            new PortfolioReturnSnapshotRebuildService(jdbcTemplate, snapshots);

    @Test
    void pendingRebuildRecapturesEveryOwnerFromItsEarliestSnapshot() throws Exception {
        stubState("PENDING");
        stubOwners(owner(7L, LocalDate.of(2026, 7, 1)), owner(8L, LocalDate.of(2026, 7, 5)));

        assertThat(service.rebuildIfPending()).isTrue();

        verify(snapshots).recaptureExistingFrom(7L, Instant.parse("2026-07-01T00:00:00Z"));
        verify(snapshots).recaptureExistingFrom(8L, Instant.parse("2026-07-05T00:00:00Z"));
    }

    @Test
    void completedRebuildIsSkipped() {
        stubState("COMPLETED");

        assertThat(service.rebuildIfPending()).isFalse();

        verify(snapshots, never()).recaptureExistingFrom(any(Long.class), any());
    }

    @Test
    void missingRebuildStateIsSkipped() {
        when(jdbcTemplate.query(contains("insights_rebuild_state"), any(RowMapper.class), any()))
                .thenReturn(List.of());

        assertThat(service.rebuildIfPending()).isFalse();

        verify(snapshots, never()).recaptureExistingFrom(any(Long.class), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubState(String status) {
        when(jdbcTemplate.query(contains("insights_rebuild_state"), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    ResultSet rows = mock(ResultSet.class);
                    when(rows.getString(1)).thenReturn(status);
                    return List.of(((RowMapper) invocation.getArgument(1)).mapRow(rows, 0));
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubOwners(ResultSet... owners) {
        when(jdbcTemplate.query(contains("min(business_date)"), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    List<Object> mapped = new ArrayList<>();
                    for (int index = 0; index < owners.length; index++) {
                        mapped.add(mapper.mapRow(owners[index], index));
                    }
                    return mapped;
                });
    }

    private static ResultSet owner(long ownerId, LocalDate earliestBusinessDate) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(ownerId);
        when(row.getObject(2, LocalDate.class)).thenReturn(earliestBusinessDate);
        return row;
    }
}
