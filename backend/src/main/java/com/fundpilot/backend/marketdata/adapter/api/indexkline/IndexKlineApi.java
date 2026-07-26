package com.fundpilot.backend.marketdata.adapter.api.indexkline;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IndexKlineApi {
    private final IndexKlineCommandHandler commands;
    private final IndexKlineQueryHandler queries;
    public boolean exists(String indexCode) { return queries.exists(indexCode); }
    public List<Bar> findAll(String indexCode) { return queries.findAll(indexCode).stream().map(b -> new Bar(b.tradeDate(), b.open(), b.high(), b.low(), b.close(), b.volume())).toList(); }
    public int upsert(String indexCode, List<Bar> bars) { return commands.upsert(indexCode, bars.stream().map(b -> new IndexKlineCommandHandler.Bar(b.tradeDate(), b.open(), b.high(), b.low(), b.close(), b.volume())).toList()); }
    public record Bar(Instant tradeDate, BigDecimal open, BigDecimal high,
                      BigDecimal low, BigDecimal close, Long volume) {}
}
