package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;

import java.time.Instant;

final class TransactionTradeDate {

    private TransactionTradeDate() {
    }

    static Instant resolve(FundTransactionEntity transaction, Instant fallbackDate) {
        return ChinaTradingDate.toUtcDate(resolveInstant(transaction, fallbackDate));
    }

    static Instant resolveInstant(FundTransactionEntity transaction, Instant fallbackDate) {
        return transaction.getTradeDate() != null
                ? transaction.getTradeDate()
                : transaction.getCreatedDate() != null ? transaction.getCreatedDate() : fallbackDate;
    }
}
