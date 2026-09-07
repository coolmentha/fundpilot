package com.fundpilot.backend.marketdata.application.query.navhistory;

import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 外部净值预取不持有本地事务，不发布行情事实。 */
@Service
@RequiredArgsConstructor
public class NavPrefetchQueryHandler {
    private final PublishedNavSourceGateway source;

    @Transactional(propagation = Propagation.NEVER)
    public List<Candidate> fetch(String fundCode) {
        var history = source.fetchHistory(fundCode);
        if (history == null || history.isEmpty()) {
            throw new IllegalStateException("基金净值历史为空");
        }
        return history.stream().map(nav -> new Candidate(nav.navDate(), nav.unitNav(), nav.accumulatedNav()))
                .toList();
    }

    public record Candidate(Instant navDate, BigDecimal unitNav, BigDecimal accumulatedNav) {}
}
