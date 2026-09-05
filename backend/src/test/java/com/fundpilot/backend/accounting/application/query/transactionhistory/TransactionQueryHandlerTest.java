package com.fundpilot.backend.accounting.application.query.transactionhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementFeeGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementNavGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionQueryHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock TransactionRepository transactions;
    @Mock TradedPortfolioFundGateway portfolioFunds;
    @Mock SettlementNavGateway navs;
    @Mock SettlementFeeGateway fees;

    @Test
    void 待确认买入返回预计净值与扣费后预计份额() {
        LedgerTransaction transaction = pending(1L, 10L, TransactionSource.INCREASE, new BigDecimal("1000"), null,
                null);
        when(portfolioFunds.findTradableByOwner(7L)).thenReturn(List.of(tradable(10L)));
        when(transactions.findByStatusOrderByTradeDateDesc(TransactionStatus.PENDING)).thenReturn(List.of(transaction));
        when(navs.unitNavOn(any(Long.class), any())).thenReturn(Optional.of(new BigDecimal("2")));
        when(fees.feeScheduleOf(11L)).thenReturn(new FeeSchedule(new BigDecimal("0.0015"), List.of()));

        var result = handler().findPendingByOwner(7L);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.confirmationState()).isEqualTo("READY");
            assertThat(view.expectedNav()).isEqualByComparingTo("2");
            assertThat(view.expectedShares()).isEqualByComparingTo("499.25");
        });
    }

    @Test
    void 转入腿在关联转出腿待确认时不可确认() {
        LedgerTransaction transaction = pending(2L, 10L, TransactionSource.TRANSFER_IN, null, null, 3L);
        LedgerTransaction related = pending(3L, 12L, TransactionSource.TRANSFER_OUT, null, new BigDecimal("10"), 2L);
        when(portfolioFunds.findTradableByOwner(7L)).thenReturn(List.of(tradable(10L)));
        when(transactions.findByStatusOrderByTradeDateDesc(TransactionStatus.PENDING)).thenReturn(List.of(transaction));
        when(transactions.findById(3L)).thenReturn(Optional.of(related));
        when(navs.unitNavOn(any(Long.class), any())).thenReturn(Optional.of(BigDecimal.ONE));

        var result = handler().findPendingByOwner(7L);

        assertThat(result).singleElement().extracting(TransactionQueryHandler.PendingResult::confirmationState)
                .isEqualTo("RELATED_PENDING");
    }

    @Test
    void 作废组合基金的待确认流水不出现在工作台() {
        LedgerTransaction transaction = pending(4L, 99L, TransactionSource.INCREASE, BigDecimal.ONE, null, null);
        when(portfolioFunds.findTradableByOwner(7L)).thenReturn(List.of(tradable(10L)));
        when(transactions.findByStatusOrderByTradeDateDesc(TransactionStatus.PENDING)).thenReturn(List.of(transaction));

        assertThat(handler().findPendingByOwner(7L)).isEmpty();
    }

    @Test
    void 查询不存在或非当前用户组合基金的交易不能静默返回空列表() {
        when(portfolioFunds.findOwned(7L, 41L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().findByPortfolioFund(7L, 41L))
                .isInstanceOf(TransactionLedgerFailure.class)
                .extracting(error -> ((TransactionLedgerFailure) error).code())
                .isEqualTo(TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
        org.mockito.Mockito.verify(transactions, never()).findByPortfolioFundOrderByTradeDateDesc(41L);
    }

    @Test
    void 待确认卖出展示冗余存储的建议来源原因() {
        LedgerTransaction transaction = LedgerTransaction.rehydrate(5L, 10L, 7L, TransactionSource.DECREASE,
                TransactionStatus.PENDING, null, new BigDecimal("10"), null, null, null,
                Instant.parse("2026-07-17T00:00:00Z"), null, null, Instant.EPOCH,
                null, null, null, 99L, null, "LOGIC_BROKEN");
        when(portfolioFunds.findTradableByOwner(7L)).thenReturn(List.of(tradable(10L)));
        when(transactions.findByStatusOrderByTradeDateDesc(TransactionStatus.PENDING)).thenReturn(List.of(transaction));
        when(navs.unitNavOn(any(Long.class), any())).thenReturn(Optional.of(BigDecimal.ONE));

        var result = handler().findPendingByOwner(7L);

        assertThat(result).singleElement().extracting(TransactionQueryHandler.PendingResult::signalReason)
                .isEqualTo("LOGIC_BROKEN");
    }

    private TransactionQueryHandler handler() {
        return new TransactionQueryHandler(transactions, portfolioFunds, navs, fees, CLOCK);
    }

    private static TradedPortfolioFundGateway.TradedPortfolioFund tradable(long id) {
        return new TradedPortfolioFundGateway.TradedPortfolioFund(id, 7L, 11L, id, true);
    }

    private static LedgerTransaction pending(long id, long portfolioFundId, TransactionSource source,
                                             BigDecimal amount, BigDecimal shares, Long relatedTransactionId) {
        return LedgerTransaction.rehydrate(id, portfolioFundId, 7L, source, TransactionStatus.PENDING, amount, shares,
                null, null, null, Instant.parse("2026-07-17T00:00:00Z"), null, null, Instant.EPOCH,
                relatedTransactionId, null, null, null, null);
    }
}
