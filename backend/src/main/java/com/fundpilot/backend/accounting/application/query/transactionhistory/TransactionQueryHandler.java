package com.fundpilot.backend.accounting.application.query.transactionhistory;

import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementFeeGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementNavGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.accounting.domain.transaction.ShareScale;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-model use case for one portfolio fund's Accounting ledger. */
@Service
@RequiredArgsConstructor
public class TransactionQueryHandler {
    private final TransactionRepository transactions;
    private final TradedPortfolioFundGateway portfolioFunds;
    private final SettlementNavGateway navs;
    private final SettlementFeeGateway fees;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TransactionLedgerCommandHandler.LedgerResult> findByPortfolioFund(long ownerId,
                                                                                   long portfolioFundId) {
        portfolioFunds.findOwned(ownerId, portfolioFundId)
                .filter(TradedPortfolioFundGateway.TradedPortfolioFund::tradable)
                .orElseThrow(() -> new TransactionLedgerFailure(
                        TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: " + portfolioFundId));
        return transactions.findByPortfolioFundOrderByTradeDateDesc(portfolioFundId).stream()
                .filter(transaction -> transaction.ownerId() == ownerId)
                .map(TransactionLedgerCommandHandler.LedgerResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TransactionLedgerCommandHandler.LedgerResult> findById(long ownerId,
                                                                                       long transactionId) {
        return transactions.findById(transactionId)
                .filter(transaction -> transaction.ownerId() == ownerId)
                .filter(transaction -> portfolioFunds.findOwned(ownerId, transaction.portfolioFundId())
                        .map(TradedPortfolioFundGateway.TradedPortfolioFund::tradable).orElse(false))
                .map(TransactionLedgerCommandHandler.LedgerResult::from);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TransactionViewResult> findViewById(long ownerId, long transactionId) {
        return findById(ownerId, transactionId).flatMap(result -> portfolioFunds
                .findOwned(ownerId, result.portfolioFundId())
                .map(fund -> new TransactionViewResult(result, fund.legacyFundId())));
    }

    /** 确认工作台：只返回当前用户仍可记账的待确认流水及其确认前置条件。 */
    @Transactional(readOnly = true)
    public List<PendingResult> findPendingByOwner(long ownerId) {
        Map<Long, TradedPortfolioFundGateway.TradedPortfolioFund> tradableFunds = new HashMap<>();
        for (TradedPortfolioFundGateway.TradedPortfolioFund fund : portfolioFunds.findTradableByOwner(ownerId)) {
            tradableFunds.put(fund.portfolioFundId(), fund);
        }
        Instant fallbackDate = clock.instant();
        return transactions.findByStatusOrderByTradeDateDesc(TransactionStatus.PENDING).stream()
                .filter(transaction -> transaction.ownerId() == ownerId)
                .map(transaction -> pending(transaction, tradableFunds.get(transaction.portfolioFundId()), fallbackDate))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvestmentPlanOccurrence> investmentPlanOccurrences(long ownerId, java.time.Instant startInclusive,
                                                                      java.time.Instant endExclusive) {
        return transactions.findInvestmentPlanOccurrences(ownerId, startInclusive, endExclusive).stream()
                .map(value -> new InvestmentPlanOccurrence(value.investmentPlanId(), value.tradeDate(),
                        value.amount(), value.status().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal investedAmount(long ownerId, Instant startInclusive, Instant endExclusive) {
        return transactions.sumInvestedAmount(ownerId, startInclusive, endExclusive);
    }

    @Transactional(readOnly = true)
    public boolean hasTransactionForAdvice(long adviceId) {
        return transactions.existsByDisciplineAdviceIdAndStatusNot(adviceId, TransactionStatus.CANCELLED);
    }

    /** 由 Discipline 建议生成的账目；回应建议后取最新一条。 */
    @Transactional(readOnly = true)
    public java.util.Optional<AdviceRelatedTransaction> findByAdvice(long adviceId) {
        return transactions.findByDisciplineAdviceId(adviceId).stream()
                .findFirst()
                .map(transaction -> new AdviceRelatedTransaction(transaction.id(), transaction.status().name()));
    }

    public record AdviceRelatedTransaction(long transactionId, String status) {}

    public record InvestmentPlanOccurrence(long investmentPlanId, java.time.Instant tradeDate,
                                            java.math.BigDecimal amount, String status) {}

    private PendingResult pending(LedgerTransaction transaction,
                                  TradedPortfolioFundGateway.TradedPortfolioFund portfolioFund,
                                  Instant fallbackDate) {
        if (portfolioFund == null) {
            return null;
        }
        Instant tradeDay = BusinessDay.toDateLabel(transaction.effectiveTradeDate(fallbackDate));
        BigDecimal expectedNav = navs.unitNavOn(portfolioFund.fundProductId(), tradeDay)
                .filter(nav -> nav.signum() > 0)
                .orElse(null);
        ConfirmationState state = confirmationState(transaction, expectedNav);
        BigDecimal expectedShares = state == ConfirmationState.READY && transaction.source().isBuy()
                ? ShareScale.normalize(transaction.amount().multiply(BigDecimal.ONE.subtract(
                        fees.feeScheduleOf(portfolioFund.fundProductId()).subscriptionRate()))
                        .divide(expectedNav, java.math.MathContext.DECIMAL64))
                : null;
        return new PendingResult(TransactionLedgerCommandHandler.LedgerResult.from(transaction),
                portfolioFund.legacyFundId(), expectedNav,
                expectedShares, state.name(), state.reason, transaction.signalReason());
    }

    private ConfirmationState confirmationState(LedgerTransaction transaction, BigDecimal expectedNav) {
        if (transaction.source() == TransactionSource.TRANSFER_IN
                && transaction.relatedTransactionId() != null
                && transactions.findById(transaction.relatedTransactionId())
                .map(related -> related.status() == TransactionStatus.PENDING).orElse(false)) {
            return ConfirmationState.RELATED_PENDING;
        }
        if (!transaction.hasRequiredInput()) {
            return ConfirmationState.INPUT_MISSING;
        }
        return expectedNav == null ? ConfirmationState.NAV_PENDING : ConfirmationState.READY;
    }

    public record TransactionViewResult(TransactionLedgerCommandHandler.LedgerResult transaction,
                                        Long legacyFundId) {
    }

    public record PendingResult(TransactionLedgerCommandHandler.LedgerResult transaction, Long legacyFundId,
                                BigDecimal expectedNav,
                                BigDecimal expectedShares, String confirmationState, String confirmationReason,
                                String signalReason) {
    }

    private enum ConfirmationState {
        READY("交易日净值已入库,可确认"),
        NAV_PENDING("等待交易日净值入库"),
        INPUT_MISSING("缺少交易金额或份额"),
        RELATED_PENDING("等待关联转换交易先确认");

        private final String reason;

        ConfirmationState(String reason) {
            this.reason = reason;
        }
    }
}
