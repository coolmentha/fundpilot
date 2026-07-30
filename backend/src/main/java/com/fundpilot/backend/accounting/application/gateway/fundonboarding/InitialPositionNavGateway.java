package com.fundpilot.backend.accounting.application.gateway.fundonboarding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** 期初持仓所需的最近已公布单位净值。 */
public interface InitialPositionNavGateway {

    Optional<PublishedNav> latest(long fundProductId);

    record PublishedNav(Instant navDate, BigDecimal unitNav) {
    }
}
