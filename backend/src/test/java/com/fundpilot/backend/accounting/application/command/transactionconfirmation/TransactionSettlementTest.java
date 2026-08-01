package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionSettlementTest {

    @Test
    void settleBuy_净额按净值折算份额不足001份_抛业务异常而非除零崩溃() {
        LedgerTransaction transaction = buy(new BigDecimal("0.004"));

        assertThatThrownBy(() -> TransactionSettlement.settleBuy(transaction,
                new BigDecimal("2.0"), FeeSchedule.none()))
                .isInstanceOf(TransactionConfirmationFailure.class)
                .extracting(error -> ((TransactionConfirmationFailure) error).code())
                .isEqualTo(TransactionConfirmationFailure.Code.AMOUNT_TOO_SMALL);
    }

    @Test
    void settleBuy_正常金额_份额与成本单价正确() {
        LedgerTransaction transaction = buy(new BigDecimal("100"));

        var purchase = TransactionSettlement.settleBuy(transaction, new BigDecimal("2.0"), FeeSchedule.none());

        assertThat(purchase.shares()).isEqualByComparingTo("50.00");
        assertThat(purchase.acquireCostPerShare()).isEqualByComparingTo("2.00");
    }

    private static LedgerTransaction buy(BigDecimal amount) {
        return LedgerTransaction.placePending(1L, 1L, TransactionSource.INCREASE,
                amount, null, Instant.parse("2026-07-27T00:00:00Z"), null, null);
    }
}
