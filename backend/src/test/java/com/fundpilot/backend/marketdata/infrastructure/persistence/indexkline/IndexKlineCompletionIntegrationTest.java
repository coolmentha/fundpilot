package com.fundpilot.backend.marketdata.infrastructure.persistence.indexkline;

import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexBar;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexKlineRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class IndexKlineCompletionIntegrationTest extends AbstractIntegrationTest {
    @Autowired IndexKlineRepository repository;
    @Autowired IndexKlineQueryHandler queries;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 盘中缓存不算完成收盘重复写仅一行且残缺与软删行不算完成() {
        String code = "test-closing-kline";
        Instant date = Instant.parse("2026-07-10T00:00:00Z");
        Instant cutoff = Instant.parse("2026-07-10T09:00:00Z");
        var bar = new IndexBar(code, date, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 0L);
        repository.upsert(List.of(bar));
        assertThat(queries.existingCodes()).contains(code);
        jdbc.update("UPDATE index_kline SET updated_date = ? WHERE index_code = ?",
                Timestamp.from(cutoff.minusSeconds(1)), code);
        assertThat(queries.completeCodesForDate(date, cutoff)).doesNotContain(code);

        repository.upsert(List.of(bar));
        repository.upsert(List.of(bar));
        assertThat(repository.findAll(code)).hasSize(1);
        assertThat(repository.findAll(code).getFirst().close()).isEqualByComparingTo(bar.close());
        assertThat(queries.completeCodesForDate(date, cutoff)).contains(code);
        assertThat(queries.completeCodesForDate(date.plusSeconds(86400), cutoff)).doesNotContain(code);

        jdbc.update("UPDATE index_kline SET open = NULL WHERE index_code = ?", code);
        assertThat(queries.completeCodesForDate(date, cutoff)).doesNotContain(code);
        repository.upsert(List.of(bar));
        jdbc.update("UPDATE index_kline SET deleted_date = CURRENT_TIMESTAMP WHERE index_code = ?", code);
        assertThat(queries.completeCodesForDate(date, cutoff)).doesNotContain(code);
        assertThat(queries.existingCodes()).doesNotContain(code);
    }
}
