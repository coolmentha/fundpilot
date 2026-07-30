package com.fundpilot.backend.marketdata.application.command.indexkline;

import com.fundpilot.backend.marketdata.domain.indexkline.IndexBar;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexKlineRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndexKlineCommandHandler {
    private final IndexKlineRepository klines;
    @Transactional public int upsert(String indexCode, List<Bar> bars) {
        return klines.upsert(bars.stream().map(bar -> new IndexBar(indexCode, bar.tradeDate(),
                bar.open(), bar.high(), bar.low(), bar.close(), bar.volume())).toList());
    }
    public record Bar(Instant tradeDate, BigDecimal open, BigDecimal high,
                      BigDecimal low, BigDecimal close, Long volume) {}
}
