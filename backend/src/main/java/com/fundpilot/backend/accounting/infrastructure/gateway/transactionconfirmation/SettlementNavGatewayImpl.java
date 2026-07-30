package com.fundpilot.backend.accounting.infrastructure.gateway.transactionconfirmation;

import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementNavGateway;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads only the requested published-NAV business day for transaction settlement. */
@Component
@RequiredArgsConstructor
public class SettlementNavGatewayImpl implements SettlementNavGateway {
    private final PublishedNavApi navs;

    @Override
    public Optional<BigDecimal> unitNavOn(long fundProductId, Instant tradeDayLabel) {
        return navs.history(fundProductId, tradeDayLabel, tradeDayLabel.plus(1, ChronoUnit.DAYS)).stream()
                .filter(nav -> nav.navDate().equals(tradeDayLabel))
                .map(PublishedNavApi.PublishedNav::unitNav)
                .findFirst();
    }
}
