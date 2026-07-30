package com.fundpilot.backend.accounting.adapter.api.position;

import com.fundpilot.backend.accounting.application.query.positiontracking.PositionQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Accounting 对外暴露的持仓事实查询契约。 */
@Component
@RequiredArgsConstructor
public class PositionApi {
    private final PositionQueryHandler queries;

    public List<Position> findByOwner(long ownerId) {
        return queries.findByOwner(ownerId).stream().map(PositionApi::from).toList();
    }

    public Optional<Position> findOwned(long ownerId, long portfolioFundId) {
        return queries.findOwned(ownerId, portfolioFundId).map(PositionApi::from);
    }

    public List<OpenLot> openLots(long ownerId, long portfolioFundId) {
        return queries.findOpenLots(ownerId, portfolioFundId).stream()
                .map(lot -> new OpenLot(lot.acquireDate(), lot.remainingShares())).toList();
    }

    private static Position from(PositionQueryHandler.PositionResult result) {
        return new Position(result.portfolioFundId(), result.ownerId(), Status.valueOf(result.status()),
                result.openedAt(), result.costPerShare(), result.confirmedShares());
    }

    public record Position(long portfolioFundId, long ownerId, Status status, Instant openedAt,
                           BigDecimal costPerShare, BigDecimal confirmedShares) {
    }

    public record OpenLot(Instant acquireDate, BigDecimal remainingShares) {
    }

    public enum Status { EMPTY, OPEN, CLEARED }
}
