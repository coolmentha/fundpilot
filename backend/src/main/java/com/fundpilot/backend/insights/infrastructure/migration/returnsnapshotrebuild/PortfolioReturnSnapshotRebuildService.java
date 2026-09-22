package com.fundpilot.backend.insights.infrastructure.migration.returnsnapshotrebuild;

import com.fundpilot.backend.insights.application.command.portfolioreturn.PortfolioReturnSnapshotCommandHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一次性重算存量组合收益快照。累计收益改为「已实现 + 未实现」后,历史点仍存着旧的现金流口径值,
 * 需要按当时账本重放一遍。复用 {@link PortfolioReturnSnapshotCommandHandler#recaptureExistingFrom},
 * 不重复实现口径,也不触碰未受影响的行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReturnSnapshotRebuildService {

    static final String REBUILD_KEY = "RETURN_TOTAL_V2";

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioReturnSnapshotCommandHandler snapshots;

    @Transactional
    public boolean rebuildIfPending() {
        List<String> statuses = jdbcTemplate.query(
                "select status from insights_rebuild_state where rebuild_key = ? for update",
                (rs, rowNum) -> rs.getString(1), REBUILD_KEY);
        if (statuses.isEmpty() || "COMPLETED".equals(statuses.get(0))) {
            return false;
        }

        List<OwnerHistory> owners = jdbcTemplate.query(
                "select owner_id, min(business_date) from portfolio_return_snapshot "
                        + "where deleted_date is null group by owner_id order by owner_id",
                (rs, rowNum) -> new OwnerHistory(rs.getLong(1),
                        rs.getObject(2, LocalDate.class).atStartOfDay(ZoneOffset.UTC).toInstant()));
        owners.forEach(owner -> snapshots.recaptureExistingFrom(owner.ownerId(), owner.earliestBusinessDate()));

        jdbcTemplate.update("update insights_rebuild_state set status = 'COMPLETED', completed_at = now(), "
                        + "details = ? where rebuild_key = ?",
                "Recaptured " + owners.size() + " owners", REBUILD_KEY);
        log.info("组合收益快照历史重算完成 owners={}", owners.size());
        return true;
    }

    private record OwnerHistory(long ownerId, Instant earliestBusinessDate) {
    }
}
