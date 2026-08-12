package com.fundpilot.backend.marketdata.adapter.api.indexvaluation;

import com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler;
import com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IndexValuationApi {
    private final IndexValuationQueryHandler queries;
    private final IndexValuationCommandHandler commands;

    public List<Valuation> history(String indexCode, String source, Instant endExclusive) {
        return queries.history(indexCode, source, endExclusive).stream().map(IndexValuationApi::from).toList();
    }

    public Optional<Valuation> latest(String indexCode, String source) {
        return queries.latest(indexCode, source).map(IndexValuationApi::from);
    }

    public int upsert(List<Input> inputs) {
        return commands.upsert(inputs.stream().map(input -> new IndexValuationCommandHandler.Input(
                input.indexCode(), input.tradeDate(), input.peRatio(), input.source())).toList());
    }

    private static Valuation from(IndexValuationQueryHandler.Valuation value) {
        return new Valuation(value.indexCode(), value.tradeDate(), value.peRatio(), value.source());
    }

    public record Input(String indexCode, Instant tradeDate, BigDecimal peRatio, String source) {}
    public record Valuation(String indexCode, Instant tradeDate, BigDecimal peRatio, String source) {}
}
