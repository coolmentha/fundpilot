package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.event.FundCreatedEvent;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 新建基金后的历史净值补齐。
 * <p>普通创建不应同步等待东方财富历史净值接口;事务提交后后台补齐,失败降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundCreatedEventListener {

    private final MarketDataFetchService marketDataFetchService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void fetchHistoryAfterCreate(FundCreatedEvent event) {
        try {
            marketDataFetchService.fetchOneFund(event.fundId());
        } catch (RuntimeException ex) {
            log.warn("建基金 {} 后异步拉取历史净值失败,降级(可稍后手动 refresh 补): {}",
                    event.fundId(), ex.getMessage());
        }
    }
}
