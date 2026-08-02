package com.fundpilot.backend.portfolio.application.query.fundtracking;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFund;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PortfolioFundQueryHandler {
    private final PortfolioFundRepository portfolioFunds;

    @Transactional(readOnly = true)
    public List<PortfolioFundResult> findByOwner(long ownerId) {
        return portfolioFunds.findByOwnerId(ownerId).stream()
                .map(PortfolioFundResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioFundResult> findOwned(long ownerId, long portfolioFundId) {
        return portfolioFunds.findById(portfolioFundId)
                .filter(portfolioFund -> portfolioFund.ownerId() == ownerId)
                .map(PortfolioFundResult::from);
    }

    @Transactional(readOnly = true)
    public List<PortfolioFundResult> findAllTracked() {
        return portfolioFunds.findAllTracked().stream().map(PortfolioFundResult::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioFundResult> findById(long portfolioFundId) {
        return portfolioFunds.findById(portfolioFundId).map(PortfolioFundResult::from);
    }

    @Transactional
    public Optional<PortfolioFundResult> findForUpdate(long portfolioFundId) {
        return portfolioFunds.findByIdForUpdate(portfolioFundId).map(PortfolioFundResult::from);
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioFundResult> findByLegacyFundId(long legacyFundId) {
        return portfolioFunds.findByLegacyFundId(legacyFundId).map(PortfolioFundResult::from);
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioFundResult> findOwnedByLegacyFundId(long ownerId, long legacyFundId) {
        return portfolioFunds.findByLegacyFundId(legacyFundId)
                .filter(portfolioFund -> portfolioFund.ownerId() == ownerId)
                .map(PortfolioFundResult::from);
    }

    public record PortfolioFundResult(long id, Long legacyFundId, long ownerId, long fundProductId,
                                      String validity, boolean positionWarningEnabled,
                                      BigDecimal positionWarningRatio, Instant voidedAt,
                                      Long voidedBy, String voidReason) {
        static PortfolioFundResult from(PortfolioFund portfolioFund) {
            return new PortfolioFundResult(portfolioFund.id(), portfolioFund.legacyFundId(), portfolioFund.ownerId(),
                    portfolioFund.fundProductId(), portfolioFund.validity().name(),
                    portfolioFund.positionWarningEnabled(), portfolioFund.positionWarningRatio(),
                    portfolioFund.voidedAt(), portfolioFund.voidedBy(), portfolioFund.voidReason());
        }
    }
}
