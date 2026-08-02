package com.fundpilot.backend.portfolio.adapter.api.fundtracking;

import com.fundpilot.backend.portfolio.application.command.fundtracking.PortfolioFundCommandHandler;
import com.fundpilot.backend.portfolio.application.command.fundtracking.PortfolioFundFailure;
import com.fundpilot.backend.portfolio.application.query.fundtracking.PortfolioFundQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortfolioFundApi {
    private final PortfolioFundCommandHandler commands;
    private final PortfolioFundQueryHandler queries;

    public PortfolioFund track(TrackPortfolioFund request) {
        try {
            return from(commands.track(request.legacyFundId(), request.ownerId(), request.fundProductId(),
                    request.positionWarningEnabled(), request.positionWarningRatio()));
        } catch (PortfolioFundFailure failure) {
            throw Failure.from(failure);
        }
    }

    public PortfolioFund configureWarning(ConfigurePositionWarning request) {
        try {
            return from(commands.configureWarning(request.ownerId(), request.portfolioFundId(),
                    request.enabled(), request.ratio()));
        } catch (PortfolioFundFailure failure) {
            throw Failure.from(failure);
        }
    }

    public List<PortfolioFund> findByOwner(long ownerId) {
        return queries.findByOwner(ownerId).stream().map(PortfolioFundApi::from).toList();
    }

    public Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId) {
        return queries.findOwned(ownerId, portfolioFundId).map(PortfolioFundApi::from);
    }

    public List<PortfolioFund> findAllTracked() {
        return queries.findAllTracked().stream().map(PortfolioFundApi::from).toList();
    }

    public Optional<PortfolioFund> findById(long portfolioFundId) {
        return queries.findById(portfolioFundId).map(PortfolioFundApi::from);
    }

    public Optional<PortfolioFund> findForUpdate(long portfolioFundId) {
        return queries.findForUpdate(portfolioFundId).map(PortfolioFundApi::from);
    }

    public Optional<PortfolioFund> findByLegacyFundId(long legacyFundId) {
        return queries.findByLegacyFundId(legacyFundId).map(PortfolioFundApi::from);
    }

    public Optional<PortfolioFund> findOwnedByLegacyFundId(long ownerId, long legacyFundId) {
        return queries.findOwnedByLegacyFundId(ownerId, legacyFundId).map(PortfolioFundApi::from);
    }

    public VoidResult voidPortfolioFund(VoidPortfolioFund request) {
        try {
            var result = commands.voidPortfolioFund(request.ownerId(), request.portfolioFundId(),
                    request.actorId(), request.reason(), request.occurredAt());
            return new VoidResult(result.id(), result.changed(), result.voidedAt(),
                    result.voidedBy(), result.voidReason());
        } catch (PortfolioFundFailure failure) {
            throw Failure.from(failure);
        }
    }

    private static PortfolioFund from(PortfolioFundCommandHandler.PortfolioFundResult result) {
        return new PortfolioFund(result.id(), null, result.ownerId(), result.fundProductId(),
                Validity.valueOf(result.validity()), result.positionWarningEnabled(),
                result.positionWarningRatio(), null, null, null);
    }

    private static PortfolioFund from(PortfolioFundQueryHandler.PortfolioFundResult result) {
        return new PortfolioFund(result.id(), result.legacyFundId(), result.ownerId(), result.fundProductId(),
                Validity.valueOf(result.validity()), result.positionWarningEnabled(),
                result.positionWarningRatio(), result.voidedAt(), result.voidedBy(), result.voidReason());
    }

    public record TrackPortfolioFund(Long legacyFundId, long ownerId, long fundProductId,
                                     boolean positionWarningEnabled,
                                     BigDecimal positionWarningRatio) {
    }

    public record ConfigurePositionWarning(long ownerId, long portfolioFundId,
                                           boolean enabled, BigDecimal ratio) {
    }

    public record VoidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                    String reason, Instant occurredAt) {
    }

    public record VoidResult(long id, boolean changed, Instant voidedAt,
                             Long voidedBy, String voidReason) {
    }

    public record PortfolioFund(long id, Long legacyFundId, long ownerId, long fundProductId, Validity validity,
                                boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                Instant voidedAt, Long voidedBy, String voidReason) {
    }

    public enum Validity {
        TRACKED,
        VOIDED
    }

    public static final class Failure extends RuntimeException {
        private final FailureCode code;

        private Failure(FailureCode code, String message) {
            super(message);
            this.code = code;
        }

        static Failure from(PortfolioFundFailure failure) {
            return new Failure(FailureCode.valueOf(failure.code().name()), failure.getMessage());
        }

        public FailureCode code() {
            return code;
        }
    }

    public enum FailureCode {
        PRODUCT_NOT_FOUND,
        PORTFOLIO_FUND_NOT_FOUND,
        PORTFOLIO_FUND_ALREADY_TRACKED,
        POSITION_WARNING_INVALID,
        PORTFOLIO_FUND_VOIDED,
        VOID_REASON_REQUIRED
    }
}
