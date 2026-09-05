package com.fundpilot.backend.portfolio.application.command.fundtracking;

import com.fundpilot.backend.portfolio.application.gateway.fundtracking.PortfolioFundEventGateway;
import com.fundpilot.backend.portfolio.application.gateway.fundtracking.TrackableProductGateway;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFund;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundRepository;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundTrackedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PortfolioFundCommandHandler {
    private final PortfolioFundRepository portfolioFunds;
    private final TrackableProductGateway products;
    private final PortfolioFundEventGateway events;
    private final Clock clock;

    @Transactional
    public PortfolioFundResult track(Long legacyFundId, long ownerId, long fundProductId,
                                     boolean positionWarningEnabled,
                                     BigDecimal positionWarningRatio) {
        if (!products.exists(fundProductId)) {
            throw new PortfolioFundFailure(PortfolioFundFailure.Code.PRODUCT_NOT_FOUND,
                    "基金产品不存在: " + fundProductId);
        }
        PortfolioFund portfolioFund;
        try {
            portfolioFund = PortfolioFund.createTracked(
                    legacyFundId, ownerId, fundProductId, positionWarningEnabled, positionWarningRatio);
        } catch (IllegalArgumentException exception) {
            throw new PortfolioFundFailure(PortfolioFundFailure.Code.POSITION_WARNING_INVALID,
                    exception.getMessage());
        }
        PortfolioFund saved = portfolioFunds.saveTrackedIfAbsent(portfolioFund)
                .orElseThrow(() -> new PortfolioFundFailure(
                        PortfolioFundFailure.Code.PORTFOLIO_FUND_ALREADY_TRACKED,
                        "该基金已在当前组合中"));
        events.publishTracked(new PortfolioFundTrackedEvent(
                saved.id(), saved.ownerId(), saved.fundProductId(), clock.instant()));
        return PortfolioFundResult.from(saved);
    }

    @Transactional
    public PortfolioFundResult configureWarning(long ownerId, long portfolioFundId,
                                                Boolean enabled, BigDecimal ratio) {
        PortfolioFund portfolioFund = ownedPortfolioFund(ownerId, portfolioFundId);
        try {
            if (enabled == null) {
                throw new IllegalArgumentException("仓位提醒启用状态不能为空");
            }
            portfolioFund.configurePositionWarning(enabled, ratio);
        } catch (IllegalArgumentException exception) {
            throw new PortfolioFundFailure(PortfolioFundFailure.Code.POSITION_WARNING_INVALID,
                    exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new PortfolioFundFailure(PortfolioFundFailure.Code.PORTFOLIO_FUND_VOIDED,
                    exception.getMessage());
        }
        return PortfolioFundResult.from(portfolioFunds.save(portfolioFund));
    }

    @Transactional
    public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                        String reason, Instant occurredAt) {
        PortfolioFund portfolioFund = ownedPortfolioFund(ownerId, portfolioFundId);
        try {
            var domainEvent = portfolioFund.voidBy(actorId, reason, occurredAt);
            if (domainEvent.isEmpty()) {
                return new VoidResult(portfolioFund.id(), false, portfolioFund.voidedAt(),
                        portfolioFund.voidedBy(), portfolioFund.voidReason());
            }
            PortfolioFund saved = portfolioFunds.save(portfolioFund);
            var voided = domainEvent.orElseThrow();
            events.publishVoided(new PortfolioFundVoidedEvent(
                    voided.portfolioFundId(), voided.ownerId(), voided.fundProductId(),
                    voided.voidedBy(), voided.reason(), voided.occurredAt()));
            return new VoidResult(saved.id(), true, saved.voidedAt(), saved.voidedBy(),
                    saved.voidReason());
        } catch (IllegalArgumentException exception) {
            throw new PortfolioFundFailure(PortfolioFundFailure.Code.VOID_REASON_REQUIRED,
                    exception.getMessage());
        }
    }

    private PortfolioFund ownedPortfolioFund(long ownerId, long portfolioFundId) {
        return portfolioFunds.findByIdForUpdate(portfolioFundId)
                .filter(portfolioFund -> portfolioFund.ownerId() == ownerId)
                .orElseThrow(() -> new PortfolioFundFailure(
                        PortfolioFundFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: " + portfolioFundId));
    }

    public record PortfolioFundResult(long id, long ownerId, long fundProductId,
                                      String validity, boolean positionWarningEnabled,
                                      BigDecimal positionWarningRatio) {
        static PortfolioFundResult from(PortfolioFund portfolioFund) {
            return new PortfolioFundResult(portfolioFund.id(), portfolioFund.ownerId(),
                    portfolioFund.fundProductId(), portfolioFund.validity().name(),
                    portfolioFund.positionWarningEnabled(), portfolioFund.positionWarningRatio());
        }
    }

    public record VoidResult(long id, boolean changed, Instant voidedAt,
                             Long voidedBy, String voidReason) {
    }
}
