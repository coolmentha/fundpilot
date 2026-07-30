package com.fundpilot.backend.marketdata.adapter.api.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeValuationQueryHandler;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeValuationApi {
    private final RealtimeValuationQueryHandler queries;

    public List<Valuation> findByFundCodes(Collection<String> fundCodes) {
        return queries.findByFundCodes(fundCodes).stream().map(value -> new Valuation(value.fundCode(),
                value.estimatedChangePct(), value.estimateTime(), value.baseNavDate(), value.status())).toList();
    }

    public record Valuation(String fundCode, BigDecimal estimatedChangePct, String estimateTime,
                            String baseNavDate, String status) {}
}
