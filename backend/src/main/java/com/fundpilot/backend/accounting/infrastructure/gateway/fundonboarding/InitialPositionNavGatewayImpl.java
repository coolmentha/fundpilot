package com.fundpilot.backend.accounting.infrastructure.gateway.fundonboarding;

import com.fundpilot.backend.accounting.application.gateway.fundonboarding.InitialPositionNavGateway;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 只读取 MarketData 已公布的单位净值，期初持仓不得使用估值或 legacy 行情实体。 */
@Component
@RequiredArgsConstructor
public class InitialPositionNavGatewayImpl implements InitialPositionNavGateway {
    private final PublishedNavApi navs;

    @Override
    public Optional<PublishedNav> latest(long fundProductId) {
        return navs.latest(fundProductId)
                .map(nav -> new PublishedNav(nav.navDate(), nav.unitNav()));
    }
}
