package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;

import java.time.Instant;

final class TransactionTradeDate {

    private TransactionTradeDate() {
    }

    static Instant resolve(FundTransactionEntity transaction, Instant fallbackDate) {
        Instant source = transaction.getTradeDate() != null
                ? transaction.getTradeDate()
                : transaction.getCreatedDate() != null ? transaction.getCreatedDate() : fallbackDate;
        return ChinaTradingDate.toUtcDate(source);
    }
}
