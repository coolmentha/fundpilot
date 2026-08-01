package com.fundpilot.backend.accounting.adapter.api.transaction;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationFailure;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.query.transactionhistory.TransactionQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Accounting's public transaction contract for other modules and inbound adapters. */
@Component
@RequiredArgsConstructor
public class TransactionApi {
    private final TransactionLedgerCommandHandler ledger;
    private final TransactionConfirmationCommandHandler confirmation;
    private final TransactionQueryHandler queries;

    public List<Transaction> findByPortfolioFund(long ownerId, long portfolioFundId) {
        return queries.findByPortfolioFund(ownerId, portfolioFundId).stream()
                .map(TransactionApi::from)
                .toList();
    }

    public java.util.Optional<Transaction> findById(long ownerId, long transactionId) {
        return queries.findById(ownerId, transactionId).map(TransactionApi::from);
    }

    public List<PendingTransaction> findPendingByOwner(long ownerId) {
        return queries.findPendingByOwner(ownerId).stream()
                .map(result -> new PendingTransaction(from(result.transaction()), result.expectedNav(),
                        result.expectedShares(), result.confirmationState(), result.confirmationReason()))
                .toList();
    }

    public List<InvestmentPlanOccurrence> investmentPlanOccurrences(long ownerId, Instant startInclusive,
                                                                      Instant endExclusive) {
        return queries.investmentPlanOccurrences(ownerId, startInclusive, endExclusive).stream()
                .map(value -> new InvestmentPlanOccurrence(value.investmentPlanId(), value.tradeDate(),
                        value.amount(), value.status()))
                .toList();
    }

    public BigDecimal investedAmount(long ownerId, Instant startInclusive, Instant endExclusive) {
        return queries.investedAmount(ownerId, startInclusive, endExclusive);
    }

    public boolean hasTransactionForAdvice(long adviceId) {
        return queries.hasTransactionForAdvice(adviceId);
    }

    public Transaction recordManual(RecordManual request) {
        try {
            return from(ledger.recordManual(request.ownerId(), request.portfolioFundId(),
                    TransactionLedgerCommandHandler.Source.valueOf(request.source().name()), request.amount(), request.shares(),
                    request.tradeDate(), request.targetPortfolioFundId()));
        } catch (TransactionLedgerFailure failure) {
            throw Failure.from(failure);
        }
    }

    public TargetHoldingResult adjustToHoldingShares(AdjustToHoldingShares request) {
        try {
            var result = ledger.adjustToHoldingShares(request.ownerId(), request.portfolioFundId(),
                    request.targetShares());
            return new TargetHoldingResult(result.previousShares(), result.targetShares(),
                    result.transaction() == null ? null : from(result.transaction()));
        } catch (TransactionLedgerFailure failure) {
            throw Failure.from(failure);
        }
    }

    public Transaction revisePending(RevisePending request) {
        try {
            return from(ledger.revisePending(request.ownerId(), request.transactionId(), request.amount(),
                    request.shares(), request.tradeDate()));
        } catch (TransactionLedgerFailure failure) {
            throw Failure.from(failure);
        }
    }

    public Transaction placePendingForAdvice(PlacePendingForAdvice request) {
        try {
            return from(ledger.placePendingForAdvice(request.ownerId(), request.portfolioFundId(),
                    TransactionLedgerCommandHandler.Source.valueOf(request.source().name()), request.amount(), request.shares(),
                    request.tradeDate(), request.disciplineAdviceId()));
        } catch (TransactionLedgerFailure failure) {
            throw Failure.from(failure);
        }
    }

    public Transaction placePendingForInvestmentPlan(PlacePendingForInvestmentPlan request) {
        try {
            return from(ledger.placePendingForInvestmentPlan(request.ownerId(), request.portfolioFundId(),
                    request.amount(), request.tradeDate(), request.investmentPlanId()));
        } catch (TransactionLedgerFailure failure) {
            throw Failure.from(failure);
        }
    }

    public List<Long> confirm(long ownerId, long transactionId) {
        try {
            return confirmation.confirm(ownerId, transactionId);
        } catch (TransactionConfirmationFailure failure) {
            throw Failure.from(failure);
        }
    }

    public List<Long> cancel(long ownerId, long transactionId) {
        try {
            return confirmation.cancel(ownerId, transactionId);
        } catch (TransactionConfirmationFailure failure) {
            throw Failure.from(failure);
        }
    }

    private static Transaction from(TransactionLedgerCommandHandler.LedgerResult result) {
        return new Transaction(result.transactionId(), result.portfolioFundId(), result.ownerId(),
                Source.valueOf(result.source()), Status.valueOf(result.status()), result.amount(), result.shares(),
                result.nav(), result.fee(), result.feeRate(), result.tradeDate(), result.confirmTime(),
                result.cancelTime(), result.createdDate(), result.relatedTransactionId(), result.signalLogId(),
                result.dcaPlanId(), result.disciplineAdviceId(), result.investmentPlanId());
    }

    public record RecordManual(long ownerId, long portfolioFundId, Source source, BigDecimal amount,
                               BigDecimal shares, Instant tradeDate, Long targetPortfolioFundId) {}
    public record AdjustToHoldingShares(long ownerId, long portfolioFundId, BigDecimal targetShares) {}
    public record TargetHoldingResult(BigDecimal previousShares, BigDecimal targetShares,
                                      Transaction transaction) {}
    public record RevisePending(long ownerId, long transactionId, BigDecimal amount, BigDecimal shares,
                                Instant tradeDate) {}
    public record PlacePendingForAdvice(long ownerId, long portfolioFundId, Source source,
                                        BigDecimal amount, BigDecimal shares, Instant tradeDate,
                                        long disciplineAdviceId) {}
    public record PlacePendingForInvestmentPlan(long ownerId, long portfolioFundId,
                                                BigDecimal amount, Instant tradeDate,
                                                long investmentPlanId) {}
    public record Transaction(long id, long portfolioFundId, long ownerId, Source source, Status status,
                              BigDecimal amount, BigDecimal shares, BigDecimal nav, BigDecimal fee,
                              BigDecimal feeRate, Instant tradeDate, Instant confirmTime, Instant cancelTime,
                              Instant createdDate, Long relatedTransactionId, Long signalLogId, Long dcaPlanId,
                              Long disciplineAdviceId, Long investmentPlanId) {}
    public record PendingTransaction(Transaction transaction, BigDecimal expectedNav, BigDecimal expectedShares,
                                     String confirmationState, String confirmationReason) {}
    public record InvestmentPlanOccurrence(long investmentPlanId, Instant tradeDate, BigDecimal amount,
                                           String status) {}
    public enum Source { INCREASE, DECREASE, TRANSFER_IN, TRANSFER_OUT, INVEST, ADJUST_IN, ADJUST_OUT }
    public enum Status { PENDING, CONFIRMED, CANCELLED }

    public static final class Failure extends RuntimeException {
        private final Code code;

        private Failure(Code code, String message) {
            super(message);
            this.code = code;
        }

        static Failure from(TransactionLedgerFailure failure) {
            return new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }

        static Failure from(TransactionConfirmationFailure failure) {
            return new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }

        public Code code() {
            return code;
        }
    }

    public enum Code {
        PORTFOLIO_FUND_NOT_FOUND,
        PORTFOLIO_FUND_NOT_TRADABLE,
        TRANSACTION_NOT_FOUND,
        TRANSACTION_ALREADY_CONFIRMED,
        TRANSACTION_ALREADY_CANCELLED,
        TRANSACTION_INPUT_REQUIRED,
        ILLEGAL_STATE_TRANSITION,
        INSUFFICIENT_HOLDING_SHARES,
        INSUFFICIENT_LOTS,
        NAV_UNAVAILABLE,
        AMOUNT_TOO_SMALL,
        ADVICE_ALREADY_RESPONDED,
        INVESTMENT_PLAN_ALREADY_EXECUTED
    }
}
