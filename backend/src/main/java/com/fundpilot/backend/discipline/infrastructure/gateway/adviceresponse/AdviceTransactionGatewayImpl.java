package com.fundpilot.backend.discipline.infrastructure.gateway.adviceresponse;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.discipline.application.gateway.adviceresponse.AdviceTransactionGateway;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 将 Discipline 的建议回应语言转换为 Accounting 的公开账目命令。 */
@Component
@RequiredArgsConstructor
public class AdviceTransactionGatewayImpl implements AdviceTransactionGateway {
    private final PositionApi positions;
    private final TransactionApi transactions;

    @Override
    public BigDecimal confirmedHoldingShares(long ownerId, long portfolioFundId) {
        return positions.findOwned(ownerId, portfolioFundId)
                .map(PositionApi.Position::confirmedShares)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public PendingTransaction createPending(CreatePending request) {
        try {
            var transaction = transactions.placePendingForAdvice(new TransactionApi.PlacePendingForAdvice(
                    request.ownerId(), request.portfolioFundId(),
                    TransactionApi.Source.valueOf(request.source().name()), request.amount(), request.shares(),
                    request.tradeDate(), request.adviceId()));
            return new PendingTransaction(transaction.id());
        } catch (TransactionApi.Failure failure) {
            throw new Rejected(failure.code() == TransactionApi.Code.ADVICE_ALREADY_RESPONDED,
                    failure.getMessage());
        }
    }

    @Override
    public boolean hasTransaction(long adviceId) {
        return transactions.hasTransactionForAdvice(adviceId);
    }

    @Override
    public java.util.Optional<RelatedTransaction> relatedTransaction(long adviceId) {
        return transactions.findByAdvice(adviceId)
                .map(value -> new RelatedTransaction(value.transactionId(), value.status()));
    }
}
