package com.fundpilot.backend.marketdata.infrastructure.persistence.indexkline;

import com.fundpilot.backend.marketdata.domain.indexkline.IndexBar;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexKlineRepository;
import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class IndexKlineRepositoryImpl implements IndexKlineRepository {
    private final JdbcTemplate jdbc;
    @Override public boolean exists(String indexCode) {
        Integer value = jdbc.queryForObject("SELECT count(*) FROM index_kline WHERE index_code = ? AND deleted_date IS NULL", Integer.class, indexCode);
        return value != null && value > 0;
    }
    @Override public List<IndexBar> findAll(String indexCode) {
        return jdbc.query("""
                SELECT index_code, trade_date, open, high, low, close, volume
                FROM index_kline WHERE index_code = ? AND deleted_date IS NULL
                ORDER BY trade_date ASC
                """, (rs, row) -> new IndexBar(rs.getString("index_code"),
                rs.getTimestamp("trade_date").toInstant(), rs.getBigDecimal("open"),
                rs.getBigDecimal("high"), rs.getBigDecimal("low"), rs.getBigDecimal("close"),
                rs.getObject("volume", Long.class)), indexCode);
    }
    @Override public int upsert(List<IndexBar> bars) {
        int changed = 0;
        for (IndexBar bar : bars) {
            changed += jdbc.update("""
                    INSERT INTO index_kline
                        (index_code, trade_date, open, high, low, close, volume,
                         version, created_date, updated_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (index_code, trade_date) WHERE deleted_date IS NULL
                    DO UPDATE SET open = EXCLUDED.open, high = EXCLUDED.high,
                        low = EXCLUDED.low, close = EXCLUDED.close, volume = EXCLUDED.volume,
                        updated_date = CURRENT_TIMESTAMP
                    """, bar.indexCode(), Timestamp.from(bar.tradeDate()), bar.open(), bar.high(),
                    bar.low(), bar.close(), bar.volume());
        }
        return changed;
    }
}
