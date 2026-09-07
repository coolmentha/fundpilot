package com.fundpilot.backend.marketdata.adapter.api.publishednav;

import com.fundpilot.backend.marketdata.application.query.navhistory.NavPrefetchQueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NavPrefetchApi {
    private final NavPrefetchQueryHandler queries;

    public List<PublishedNavApi.NavCandidate> fetch(String fundCode) {
        return queries.fetch(fundCode).stream().map(nav -> new PublishedNavApi.NavCandidate(
                nav.navDate(), nav.unitNav(), nav.accumulatedNav())).toList();
    }
}
