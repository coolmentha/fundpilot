package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 待确认账目补偿编排；每个 PortfolioFund 调用独立的确认事务。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionCompensationCommandHandler {
    private final TransactionConfirmationCommandHandler confirmations;

    public int compensateAll(Instant fallbackDate) {
        int confirmed = 0;
        for (Long portfolioFundId : confirmations.portfolioFundsWithPendingTransactions()) {
            try {
                confirmed += confirmations.confirmPendingFor(portfolioFundId, fallbackDate);
            } catch (RuntimeException exception) {
                log.error("Accounting 待确认交易补偿失败 portfolio_fund={} message={}", portfolioFundId,
                        exception.getMessage(), exception);
            }
        }
        return confirmed;
    }
}
