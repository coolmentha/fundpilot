package com.fundpilot.backend.marketdata.application.gateway.indicatorcompute;

import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway.InvestmentTarget;
import java.util.Optional;

/** 按需计算指标时所需的基金静态信息（代码、基准指数、投资标的）。 */
public interface IndicatorFundGateway {

    Optional<IndicatorFund> findById(long fundProductId);

    record IndicatorFund(long fundProductId, String fundCode, String fundName,
                         String benchmarkIndexCode, InvestmentTarget investmentTarget) {
    }
}