package com.fundpilot.backend.marketdata.infrastructure.persistence.indicator;

import com.fundpilot.backend.marketdata.domain.indicator.MarketIndicator;
import com.fundpilot.backend.marketdata.domain.indicator.MarketIndicatorRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class MarketIndicatorRepositoryImpl implements MarketIndicatorRepository {
    private final JdbcTemplate jdbc;
    @Override public Optional<MarketIndicator> find(long productId, Instant date) {
        List<MarketIndicator> values = jdbc.query("""
                SELECT fund_product_id, fund_code, snapshot_date, current_nav,
                       price_above_year_line, year_line_rising, weekly_macd_state,
                       volume_state, weekly_drop_percent, is_sixty_day_high
                FROM market_indicator_snapshot
                WHERE fund_product_id = ? AND snapshot_date = ? AND deleted_date IS NULL
                """, (rs, row) -> map(rs), productId, sqlDate(date));
        return values.stream().findFirst();
    }
    @Override public MarketIndicator upsert(Long legacyFundId, MarketIndicator i) {
        jdbc.update("""
                INSERT INTO market_indicator_snapshot
                    (fund_id, fund_product_id, fund_code, snapshot_date, current_nav,
                     price_above_year_line, year_line_rising, weekly_macd_state,
                     volume_state, weekly_drop_percent, is_sixty_day_high,
                     version, created_date, updated_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (fund_product_id, snapshot_date) WHERE deleted_date IS NULL
                DO UPDATE SET current_nav = EXCLUDED.current_nav,
                    price_above_year_line = EXCLUDED.price_above_year_line,
                    year_line_rising = EXCLUDED.year_line_rising,
                    weekly_macd_state = EXCLUDED.weekly_macd_state,
                    volume_state = EXCLUDED.volume_state,
                    weekly_drop_percent = EXCLUDED.weekly_drop_percent,
                    is_sixty_day_high = EXCLUDED.is_sixty_day_high,
                    updated_date = CURRENT_TIMESTAMP
                """, legacyFundId, i.fundProductId(), i.fundCode(), sqlDate(i.snapshotDate()), i.currentNav(), i.priceAboveYearLine(), i.yearLineRising(), i.weeklyMacdState(), i.volumeState(), i.weeklyDropPercent(), i.sixtyDayHigh());
        return find(i.fundProductId(), i.snapshotDate()).orElseThrow();
    }
    private static MarketIndicator map(ResultSet rs) throws SQLException {
        return new MarketIndicator(rs.getLong("fund_product_id"), rs.getString("fund_code"),
                rs.getDate("snapshot_date").toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                rs.getBigDecimal("current_nav"), rs.getObject("price_above_year_line", Boolean.class),
                rs.getBoolean("year_line_rising"), rs.getString("weekly_macd_state"),
                rs.getString("volume_state"), rs.getBigDecimal("weekly_drop_percent"),
                rs.getBoolean("is_sixty_day_high"));
    }
    private static Date sqlDate(Instant value) { return Date.valueOf(value.atZone(ZoneOffset.UTC).toLocalDate()); }
}
