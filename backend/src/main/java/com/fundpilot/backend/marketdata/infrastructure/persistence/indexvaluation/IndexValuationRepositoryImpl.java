package com.fundpilot.backend.marketdata.infrastructure.persistence.indexvaluation;

import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuation;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuationRepository;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class IndexValuationRepositoryImpl implements IndexValuationRepository {
    private final JdbcTemplate jdbc;

    @Override
    public List<IndexValuation> findHistory(String indexCode, String source, Instant endExclusive) {
        return jdbc.query("""
                SELECT index_code, trade_date, pe_ratio, source
                FROM index_valuation
                WHERE index_code = ? AND source = ? AND trade_date < ?
                ORDER BY trade_date ASC
                """, (rs, row) -> new IndexValuation(rs.getString("index_code"),
                rs.getTimestamp("trade_date").toInstant(), rs.getBigDecimal("pe_ratio"),
                rs.getString("source")), indexCode, source, Timestamp.from(endExclusive));
    }

    @Override
    public Optional<IndexValuation> findLatest(String indexCode, String source) {
        List<IndexValuation> values = jdbc.query("""
                SELECT index_code, trade_date, pe_ratio, source
                FROM index_valuation WHERE index_code = ? AND source = ?
                ORDER BY trade_date DESC LIMIT 1
                """, (rs, row) -> new IndexValuation(rs.getString("index_code"),
                rs.getTimestamp("trade_date").toInstant(), rs.getBigDecimal("pe_ratio"),
                rs.getString("source")), indexCode, source);
        return values.stream().findFirst();
    }

    @Override
    public int upsert(List<IndexValuation> valuations) {
        if (valuations.isEmpty()) return 0;
        int[] changed = jdbc.batchUpdate("""
                INSERT INTO index_valuation
                    (index_code, trade_date, pe_ratio, source, version, created_date, updated_date)
                VALUES (?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (index_code, trade_date, source)
                DO UPDATE SET pe_ratio = EXCLUDED.pe_ratio, updated_date = CURRENT_TIMESTAMP
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                IndexValuation valuation = valuations.get(index);
                statement.setString(1, valuation.indexCode());
                statement.setTimestamp(2, Timestamp.from(valuation.tradeDate()));
                statement.setBigDecimal(3, valuation.peRatio());
                statement.setString(4, valuation.source());
            }

            @Override
            public int getBatchSize() {
                return valuations.size();
            }
        });
        return Arrays.stream(changed).sum();
    }
}
