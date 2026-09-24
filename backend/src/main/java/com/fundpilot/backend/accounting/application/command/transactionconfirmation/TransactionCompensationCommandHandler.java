package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 待确认账目补偿编排；每个 PortfolioFund 调用独立的确认事务。
 *
 * <p>确认日期恒取自流水自身的 {@code trade_date}（由领域在创建时强制非空），
 * 因此不再由调度入口传入兜底日期。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionCompensationCommandHandler {
    private final TransactionConfirmationCommandHandler confirmations;

    public int compensateAll() {
        int confirmed = 0;
        for (Long portfolioFundId : confirmations.portfolioFundsWithPendingTransactions()) {
            try {
                confirmed += confirmations.confirmPendingFor(portfolioFundId);
            } catch (RuntimeException exception) {
                log.error("Accounting 待确认交易补偿失败 portfolio_fund={} message={}", portfolioFundId,
                        exception.getMessage(), exception);
            }
        }
        return confirmed;
    }
}
