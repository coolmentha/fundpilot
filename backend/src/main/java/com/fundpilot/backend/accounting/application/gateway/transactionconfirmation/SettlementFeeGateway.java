package com.fundpilot.backend.accounting.application.gateway.transactionconfirmation;

import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;

/** 结算所需费率的出站契约；费率缺失时返回 {@link FeeSchedule#none()} 降级不扣费。 */
public interface SettlementFeeGateway {

    FeeSchedule feeScheduleOf(long fundProductId);
}
