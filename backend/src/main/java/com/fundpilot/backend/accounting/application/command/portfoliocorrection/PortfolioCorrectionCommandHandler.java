package com.fundpilot.backend.accounting.application.command.portfoliocorrection;

import com.fundpilot.backend.accounting.application.gateway.portfoliocorrection.CorrectablePortfolioFundGateway;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCreated;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.position.PositionStatus;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.PendingTransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
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
    private static final int NUMERIC_PRECISION = 19;
    private static final int NUMERIC_SCALE = 8;

    private final CorrectablePortfolioFundGateway portfolioFunds;
    private final PositionRepository positions;
    private final TransactionRepository transactions;
    private final PendingTransactionRepository pendingTransactions;
    private final LedgerEventGateway events;
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
        BigDecimal normalizedCost = normalizePositiveNumeric(costPerShare);
        if (normalizedCost == null) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID,
                    "成本单价必须大于 0 且不超过数据库可表示范围");
        }
        var position = positions.findByPortfolioFund(portfolioFundId)
                .filter(existing -> existing.ownerId() == ownerId
                        && existing.status() == PositionStatus.OPEN)
                .orElseThrow(() -> new PortfolioCorrectionFailure(
                        PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_OPEN,
                        "只有当前持仓可以修改成本单价"));
        var confirmed = transactions.findByPortfolioFundAndStatus(portfolioFundId, TransactionStatus.CONFIRMED);
        BigDecimal holdingShares = LedgerReplay.netShares(confirmed);
        if (holdingShares.signum() <= 0) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_OPEN,
                    "当前确认持仓份额无效，不能修改成本单价");
        }
        BigDecimal storedAmount = normalizePositiveNumeric(holdingShares.multiply(normalizedCost));
        if (storedAmount == null) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID,
                    "成本总额不在数据库可表示范围");
        }
        BigDecimal persistedCost = LedgerTransaction.costPerShareFromStoredAmount(storedAmount, holdingShares);
        if (normalizePositiveNumeric(persistedCost) == null) {
            throw new PortfolioCorrectionFailure(
                    PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID,
                    "成本单价不在数据库可表示范围");
        }
        Instant correctedAt = clock.instant();
        LedgerTransaction reset = LedgerTransaction.recordCostBasisReset(
                portfolioFundId, ownerId, holdingShares, normalizedCost, correctedAt);
        position.correctCostPerShare(persistedCost);
        LedgerTransaction savedReset = transactions.save(reset);
        var saved = positions.save(position);
        events.publishCreated(new TransactionCreated(savedReset.id(), savedReset.portfolioFundId(),
                savedReset.ownerId(), savedReset.source().name(), savedReset.amount(), savedReset.shares(),
                savedReset.tradeDate(), correctedAt));
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
        var portfolioFund = portfolioFunds.findOwnedForUpdate(ownerId, portfolioFundId)
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

    private static BigDecimal normalizePositiveNumeric(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.setScale(NUMERIC_SCALE, RoundingMode.HALF_UP);
        return normalized.signum() > 0 && normalized.precision() <= NUMERIC_PRECISION
                ? normalized : null;
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
