package com.fundpilot.backend.marketdata.infrastructure.persistence.watchedindex;

import com.fundpilot.backend.marketdata.domain.watchedindex.WatchedIndexList;
import com.fundpilot.backend.marketdata.domain.watchedindex.WatchedIndexRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class WatchedIndexRepositoryImpl implements WatchedIndexRepository {
    private final JdbcTemplate jdbc;
    @Override public WatchedIndexList findByOwnerId(long ownerId) {
        return new WatchedIndexList(ownerId, jdbc.queryForList("""
                SELECT index_code FROM market_watched_index
                WHERE owner_id = ? AND deleted_date IS NULL
                ORDER BY display_order, id
                """, String.class, ownerId));
    }
    @Override public List<String> findAllDistinctCodes() {
        return jdbc.queryForList("""
                SELECT index_code FROM market_watched_index WHERE deleted_date IS NULL
                GROUP BY index_code ORDER BY min(display_order), index_code
                """, String.class);
    }
    @Override public WatchedIndexList replace(WatchedIndexList watchlist) {
        jdbc.update("UPDATE market_watched_index SET deleted_date = CURRENT_TIMESTAMP, updated_date = CURRENT_TIMESTAMP WHERE owner_id = ? AND deleted_date IS NULL", watchlist.ownerId());
        for (int i = 0; i < watchlist.indexCodes().size(); i++) {
            jdbc.update("""
                    INSERT INTO market_watched_index
                        (owner_id, index_code, display_order, version, created_date, updated_date)
                    VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, watchlist.ownerId(), watchlist.indexCodes().get(i), i);
        }
        return watchlist;
    }
}
