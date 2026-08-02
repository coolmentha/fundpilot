package com.fundpilot.backend.accounting.application.gateway.transactionledger;

import java.time.Instant;
import java.util.Optional;

/**
 * 交易发生日对交易日历的出站契约。
 * <p>创建待确认账目时把默认或用户输入的 {@code tradeDate} 规范到最近交易日，
 * 保证确认用例一定能查到该日净值，避免 PENDING 永久卡死。
 */
public interface TradingDayGateway {

    /** 返回 {@code date} 当天或之前的最近交易日；日历为空时返回空。 */
    Optional<Instant> latestTradingDayOnOrBefore(Instant date);
}
