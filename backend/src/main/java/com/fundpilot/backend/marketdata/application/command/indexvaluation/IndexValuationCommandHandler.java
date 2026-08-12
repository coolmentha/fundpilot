package com.fundpilot.backend.marketdata.application.command.indexvaluation;

import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuation;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndexValuationCommandHandler {
    private final IndexValuationRepository valuations;

    @Transactional
    public int upsert(List<Input> inputs) {
        return valuations.upsert(inputs.stream().map(input -> new IndexValuation(input.indexCode(),
                input.tradeDate(), input.peRatio(), input.source())).toList());
    }

    public record Input(String indexCode, java.time.Instant tradeDate, java.math.BigDecimal peRatio,
                        String source) {}
}
