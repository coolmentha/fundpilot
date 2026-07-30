package com.fundpilot.backend.marketdata.application.query.indexkline;

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
public class IndexKlineQueryHandler {
    private final IndexKlineRepository klines;
    @Transactional(readOnly = true) public boolean exists(String indexCode) { return klines.exists(indexCode); }
    @Transactional(readOnly = true) public List<Bar> findAll(String indexCode) {
        return klines.findAll(indexCode).stream().map(Bar::from).toList();
    }
    public record Bar(Instant tradeDate, BigDecimal open, BigDecimal high,
                      BigDecimal low, BigDecimal close, Long volume) {
        static Bar from(IndexBar bar) { return new Bar(bar.tradeDate(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()); }
    }
}
