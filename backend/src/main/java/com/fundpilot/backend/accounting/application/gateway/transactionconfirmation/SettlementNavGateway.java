package com.fundpilot.backend.accounting.application.gateway.transactionconfirmation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** 结算所需成交净值的出站契约；只取交易发生日的单位净值，禁止用次日或最新一期替代。 */
public interface SettlementNavGateway {

    Optional<BigDecimal> unitNavOn(long fundProductId, Instant tradeDayLabel);
}
