package com.fundpilot.backend.accounting.application.command.portfoliocorrection;

import com.fundpilot.backend.accounting.application.gateway.portfoliocorrection.CorrectablePortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.position.PositionStatus;
import com.fundpilot.backend.accounting.domain.transaction.PendingTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PortfolioCorrectionCommandHandler {
    private final CorrectablePortfolioFundGateway portfolioFunds;
    private final PositionRepository positions;
    private final PendingTransactionRepository pendingTransactions;
    private final Clock clock;

    @Transactional
    public CostCorrectionResult correctCostPerShare(long ownerId, long portfolioFundId,
                                                     BigDecimal costPerShare) {
        var portfolioFund = portfolioFunds.findOwnedForUpdate(ownerId, portfolioFundId)
                .orElseThrow(() -> new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: " + portfolioFundId));
        if (portfolioFund.validity() != CorrectablePortfolioFundGateway.Validity.TRACKED) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                    "组合基金不存在: " + portfolioFundId);
        }
        BigDecimal normalizedCost = costPerShare == null ? null
                : costPerShare.setScale(8, RoundingMode.HALF_UP);
        if (normalizedCost == null || normalizedCost.signum() <= 0) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID,
                    "成本单价必须大于 0");
        }
        var position = positions.findByPortfolioFund(portfolioFundId)
                .filter(existing -> existing.ownerId() == ownerId
                        && existing.status() == PositionStatus.OPEN)
                .orElseThrow(() -> new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_OPEN,
                        "只有当前持仓可以修改成本单价"));
        position.correctCostPerShare(normalizedCost);
        var saved = positions.save(position);
        return new CostCorrectionResult(saved.portfolioFundId(), saved.costPerShare());
    }

    @Transactional
    public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId,
                                        String reason, boolean confirmed) {
        if (!confirmed) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.VOID_CONFIRMATION_REQUIRED,
                    "必须明确确认作废操作");
        }
        String normalizedReason = requireReason(reason);
        var portfolioFund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: " + portfolioFundId));
        if (portfolioFund.validity() == CorrectablePortfolioFundGateway.Validity.TRACKED
                && pendingTransactions.existsByPortfolioFund(portfolioFundId, portfolioFund.legacyFundId())) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS,
                    "组合基金存在待确认交易，不能作废");
        }

        try {
            var result = portfolioFunds.voidPortfolioFund(
                    ownerId, portfolioFundId, ownerId, normalizedReason, clock.instant());
            return new VoidResult(result.id(), result.changed(), result.voidedAt(),
                    result.voidedBy(), result.voidReason());
        } catch (CorrectablePortfolioFundGateway.Rejected rejected) {
            throw mapRejected(portfolioFundId, rejected);
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.VOID_REASON_REQUIRED,
                    "作废原因不能为空");
        }
        return reason.trim();
    }

    private PortfolioCorrectionFailure mapRejected(
            long portfolioFundId, CorrectablePortfolioFundGateway.Rejected rejected) {
        return switch (rejected.reason()) {
            case NOT_FOUND -> new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                    "组合基金不存在: " + portfolioFundId);
            case INVALID_REASON -> new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.VOID_REASON_REQUIRED,
                    rejected.getMessage());
            case CONFLICT -> new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_CORRECTION_CONFLICT,
                    "组合基金状态已变化，请刷新后重试");
        };
    }

    public record VoidResult(long portfolioFundId, boolean changed, Instant voidedAt,
                             Long voidedBy, String voidReason) {
    }

    public record CostCorrectionResult(long portfolioFundId, BigDecimal costPerShare) {
    }
}
