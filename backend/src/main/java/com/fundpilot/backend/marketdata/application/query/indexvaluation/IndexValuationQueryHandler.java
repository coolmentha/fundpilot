package com.fundpilot.backend.marketdata.application.query.indexvaluation;

import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuation;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndexValuationQueryHandler {
    private final IndexValuationRepository valuations;

    @Transactional(readOnly = true)
    public List<Valuation> history(String indexCode, String source, Instant endExclusive) {
        return valuations.findHistory(indexCode, source, endExclusive).stream()
                .map(IndexValuationQueryHandler::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Valuation> latest(String indexCode, String source) {
        return valuations.findLatest(indexCode, source).map(IndexValuationQueryHandler::from);
    }

    private static Valuation from(IndexValuation value) {
        return new Valuation(value.indexCode(), value.tradeDate(), value.peRatio(), value.source());
    }

    public record Valuation(String indexCode, Instant tradeDate, java.math.BigDecimal peRatio, String source) {}
}
