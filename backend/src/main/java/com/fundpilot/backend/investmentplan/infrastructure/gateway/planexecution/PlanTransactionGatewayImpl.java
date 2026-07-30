package com.fundpilot.backend.investmentplan.infrastructure.gateway.planexecution;

import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanTransactionGatewayImpl implements PlanTransactionGateway {
    private final TransactionApi transactions;
    @Override public void createPending(long ownerId, long portfolioFundId, BigDecimal amount, Instant tradeDate,
                                        long planId) {
        try {
            transactions.placePendingForInvestmentPlan(new TransactionApi.PlacePendingForInvestmentPlan(
                    ownerId, portfolioFundId, amount, tradeDate, planId));
        } catch (TransactionApi.Failure failure) {
            if (failure.code() == TransactionApi.Code.INVESTMENT_PLAN_ALREADY_EXECUTED) {
                throw new AlreadyExecuted(failure.getMessage());
            }
            throw failure;
        }
    }

    @Override public java.util.List<Occurrence> occurrences(long ownerId, Instant startInclusive, Instant endExclusive) {
        return transactions.investmentPlanOccurrences(ownerId, startInclusive, endExclusive).stream()
                .map(value -> new Occurrence(value.investmentPlanId(), value.tradeDate(), value.amount(), value.status()))
                .toList();
    }

    @Override public BigDecimal investedAmount(long ownerId, Instant startInclusive, Instant endExclusive) {
        return transactions.investedAmount(ownerId, startInclusive, endExclusive);
    }
}
