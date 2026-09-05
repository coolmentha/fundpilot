package com.fundpilot.backend.accounting.domain.ledgerreplay;

import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerReplayTest {

    @Test
    void resetDropsPreviousCostAndWeightsLaterBuy() {
        LedgerTransaction before = buy(1L, "2026-08-01T00:00:00Z", "100", "100");
        LedgerTransaction reset = reset(2L, "2026-08-02T00:00:00Z", "100", "1.20");
        LedgerTransaction after = buy(3L, "2026-08-03T00:00:00Z", "30", "20");

        assertThat(LedgerReplay.replayCostPerShare(List.of(before, reset, after)))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("1.25"));
    }

    @Test
    void businessDateBeforeResetWinsWhenConfirmationWasLater() {
        LedgerTransaction originalHolding = buy(1L, "2026-08-01T00:00:00Z", "100", "100");
        LedgerTransaction reset = reset(2L, "2026-08-02T00:00:00Z", "100", "1.20");
        LedgerTransaction earlierBusinessDate = buy(3L, "2026-08-01T00:00:00Z", "30", "20");
        LedgerTransaction after = buy(4L, "2026-08-03T00:00:00Z", "30", "20");

        assertThat(LedgerReplay.replayCostPerShare(List.of(reset, earlierBusinessDate, originalHolding, after)))
                .hasValueSatisfying(value -> assertThat(value)
                        .isEqualByComparingTo("1.242857142857143"));
    }

    @Test
    void latestResetBecomesNewCostAnchor() {
        LedgerTransaction firstReset = reset(2L, "2026-08-02T00:00:00Z", "100", "1.20");
        LedgerTransaction firstBuy = buy(3L, "2026-08-03T00:00:00Z", "30", "20");
        LedgerTransaction latestReset = reset(4L, "2026-08-04T00:00:00Z", "120", "2.00");
        LedgerTransaction after = buy(5L, "2026-08-05T00:00:00Z", "10", "10");

        assertThat(LedgerReplay.replayCostPerShare(List.of(firstReset, firstBuy, latestReset, after)))
                .hasValueSatisfying(value -> assertThat(value)
                        .isEqualByComparingTo("1.923076923076923"));
    }

    @Test
    void resetAbsorbsEarlierAdjustmentAndLaterAdjustmentRemainsZeroCost() {
        LedgerTransaction originalHolding = buy(1L, "2026-08-01T00:00:00Z", "100", "100");
        LedgerTransaction earlierAdjustment = adjustment(2L, "2026-08-02T00:00:00Z", "20");
        LedgerTransaction reset = reset(3L, "2026-08-03T00:00:00Z", "120", "1.20");
        LedgerTransaction laterAdjustment = adjustment(4L, "2026-08-04T00:00:00Z", "10");
        LedgerTransaction laterBuy = buy(5L, "2026-08-05T00:00:00Z", "30", "20");

        assertThat(LedgerReplay.replayCostPerShare(List.of(
                laterBuy, reset, earlierAdjustment, originalHolding, laterAdjustment)))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("1.16"));
    }

    @Test
    void noResetKeepsLegacyIncrementalPath() {
        assertThat(LedgerReplay.replayCostPerShare(List.of(
                buy(1L, "2026-08-01T00:00:00Z", "100", "100"))))
                .isEmpty();
    }

    @Test
    void historicalReplayDilutesFirstPurchaseWithEarlierZeroCostShares() {
        assertThat(LedgerReplay.replayHistoricalCostPerShare(List.of(
                adjustment(1L, "2026-08-01T00:00:00Z", "100"),
                buy(2L, "2026-08-02T00:00:00Z", "1000", "100"))))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("5"));
    }

    @Test
    void costBasisResetStoresConfirmedSnapshotWithoutChangingShares() {
        Instant occurredAt = Instant.parse("2026-08-02T00:00:00Z");

        LedgerTransaction transaction = LedgerTransaction.recordCostBasisReset(
                11L, 3L, new BigDecimal("100"), new BigDecimal("1.20"), occurredAt);

        assertThat(transaction.source()).isEqualTo(TransactionSource.COST_BASIS_RESET);
        assertThat(transaction.status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(transaction.amount()).isEqualByComparingTo("120.00000000");
        assertThat(transaction.shares()).isEqualByComparingTo("100.00");
        assertThat(transaction.signedShares()).isZero();
        assertThat(transaction.tradeDate()).isEqualTo(occurredAt);
        assertThat(transaction.confirmTime()).isEqualTo(occurredAt);
        assertThat(transaction.nav()).isNull();
        assertThat(transaction.fee()).isNull();
    }

    @Test
    void resetReplayUsesStoredAmountScaleForFractionalShares() {
        LedgerTransaction transaction = LedgerTransaction.recordCostBasisReset(
                11L, 3L, new BigDecimal("0.07"), new BigDecimal("1.23456789"),
                Instant.parse("2026-08-02T00:00:00Z"));

        assertThat(transaction.amount()).isEqualByComparingTo("0.08641975");
        assertThat(LedgerReplay.replayCostPerShare(List.of(transaction))).hasValueSatisfying(value ->
                assertThat(value).isEqualByComparingTo("1.23456786"));
    }

    private static LedgerTransaction buy(long id, String date, String amount, String shares) {
        return LedgerTransaction.rehydrate(id, 11L, 3L, TransactionSource.INCREASE,
                TransactionStatus.CONFIRMED, new BigDecimal(amount), new BigDecimal(shares),
                BigDecimal.ONE, null, null, Instant.parse(date), Instant.parse(date), null,
                Instant.parse(date), null, null, null, null, null);
    }

    private static LedgerTransaction reset(long id, String date, String shares, String cost) {
        BigDecimal normalizedShares = new BigDecimal(shares);
        return LedgerTransaction.rehydrate(id, 11L, 3L, TransactionSource.COST_BASIS_RESET,
                TransactionStatus.CONFIRMED, normalizedShares.multiply(new BigDecimal(cost)),
                normalizedShares, null, null, null, Instant.parse(date), Instant.parse(date), null,
                Instant.parse(date), null, null, null, null, null);
    }

    private static LedgerTransaction adjustment(long id, String date, String shares) {
        return LedgerTransaction.rehydrate(id, 11L, 3L, TransactionSource.ADJUST_IN,
                TransactionStatus.CONFIRMED, null, new BigDecimal(shares), null, null, null,
                Instant.parse(date), Instant.parse(date), null, Instant.parse(date),
                null, null, null, null, null);
    }
}
