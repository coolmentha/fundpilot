package com.fundpilot.backend.accounting.adapter.api.returnfacts;

import com.fundpilot.backend.accounting.application.query.returnfacts.AccountingReturnQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Accounting 对外暴露的已确认账本收益事实。 */
@Component
@RequiredArgsConstructor
public class AccountingReturnApi {
    private final AccountingReturnQueryHandler queries;

    public List<ReturnFact> findByOwner(long ownerId) {
        return queries.findByOwner(ownerId).stream().map(AccountingReturnApi::from).toList();
    }

    public List<ReturnFact> findByOwnerAt(long ownerId, Instant endExclusive) {
        return queries.findByOwnerAt(ownerId, endExclusive).stream().map(AccountingReturnApi::from).toList();
    }

    private static ReturnFact from(AccountingReturnQueryHandler.ReturnFact value) {
        return new ReturnFact(value.portfolioFundId(), value.investedAmount(), value.redeemedAmount(),
                value.externalInvestedAmount(), value.externalRedeemedAmount(), value.feeAmount(),
                value.realizedPnl(), value.realizedComplete());
    }

    public record ReturnFact(long portfolioFundId, BigDecimal investedAmount, BigDecimal redeemedAmount,
                             BigDecimal externalInvestedAmount, BigDecimal externalRedeemedAmount,
                             BigDecimal feeAmount, BigDecimal realizedPnl, boolean realizedComplete) {
    }
}
