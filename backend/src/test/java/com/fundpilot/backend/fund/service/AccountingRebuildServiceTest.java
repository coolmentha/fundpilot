package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingRebuildServiceTest extends AbstractIntegrationTest {

    @Autowired AccountingRebuildService accountingRebuildService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void 重建已确认交易并重置止盈周期且只执行一次() {
        long fundId = jdbcTemplate.queryForObject(
                "insert into fund(fund_code,fund_name,status,cost_per_share,version,created_date,updated_date) " +
                        "values('REBUILD001','重建测试','HOLDING',9,0,now(),now()) returning id", Long.class);
        long targetFundId = jdbcTemplate.queryForObject(
                "insert into fund(fund_code,fund_name,status,cost_per_share,version,created_date,updated_date) " +
                        "values('REBUILD002','转换目标','HOLDING',9,0,now(),now()) returning id", Long.class);
        insertNav(fundId, "2026-07-01T00:00:00Z", "1.00", "2.00");
        insertNav(fundId, "2026-07-02T00:00:00Z", "1.20", "2.40");
        insertNav(targetFundId, "2026-07-02T00:00:00Z", "2.00", "3.00");
        long buyId = insertTx(fundId, "INCREASE", "1000", "400", "2.50", "0.0015",
                "2026-07-01T06:00:00Z");
        long sellId = insertTx(fundId, "DECREASE", null, "100", "2.40", "0.01",
                "2026-07-02T06:00:00Z");
        long transferOutId = insertTx(fundId, "TRANSFER_OUT", null, "100", "2.40", "0.01",
                "2026-07-02T07:00:00Z");
        long transferInId = insertTx(targetFundId, "TRANSFER_IN", "999", "1", "3.00", "0",
                "2026-07-02T07:00:00Z");
        jdbcTemplate.update("update fund_transaction set related_fund_transaction_id=? where id=?",
                transferInId, transferOutId);
        jdbcTemplate.update("update fund_transaction set related_fund_transaction_id=? where id=?",
                transferOutId, transferInId);
        jdbcTemplate.update("insert into fund_lot(fund_id,acquire_tx_id,acquire_date,acquire_shares," +
                        "remaining_shares,acquire_cost_per_share,version,created_date,updated_date) " +
                        "values(?,?,?,?,?,?,0,now(),now())",
                fundId, buyId, Timestamp.from(Instant.parse("2026-07-01T06:00:00Z")),
                new BigDecimal("400"), new BigDecimal("300"), new BigDecimal("2.50"));
        jdbcTemplate.update("insert into fund_strategy(fund_id,status,take_profit_phase,cycle_started_at," +
                        "cycle_peak_nav,triggered_signal_id,cooldown_started_at,customized,version,created_date,updated_date) " +
                        "values(?,'EFFECTIVE','HARVESTING',now(),3,99,now(),true,0,now(),now())", fundId);
        jdbcTemplate.update("insert into accounting_rebuild_state(rebuild_key,status) values(?, 'PENDING') " +
                        "on conflict(rebuild_key) do update set status='PENDING',completed_at=null",
                AccountingRebuildService.REBUILD_KEY);

        assertThat(accountingRebuildService.rebuildIfPending()).isTrue();

        assertThat(decimal("select nav from fund_transaction where id=?", buyId)).isEqualByComparingTo("1.00");
        assertThat(decimal("select shares from fund_transaction where id=?", buyId))
                .isEqualByComparingTo(new BigDecimal("998.5"));
        assertThat(decimal("select acquire_cost_per_share from fund_lot where acquire_tx_id=?", buyId))
                .isEqualByComparingTo("1.00150225");
        assertThat(decimal("select remaining_shares from fund_lot where acquire_tx_id=?", buyId))
                .isEqualByComparingTo("798.5");
        assertThat(decimal("select amount from fund_transaction where id=?", sellId)).isEqualByComparingTo("118.8");
        assertThat(decimal("select amount from fund_transaction where id=?", transferInId)).isEqualByComparingTo("118.8");
        assertThat(decimal("select shares from fund_transaction where id=?", transferInId)).isEqualByComparingTo("59.4");
        assertThat(jdbcTemplate.queryForObject("select take_profit_phase from fund_strategy where fund_id=?",
                String.class, fundId)).isEqualTo("ACCUMULATING");
        assertThat(jdbcTemplate.queryForObject("select cycle_peak_nav is null from fund_strategy where fund_id=?",
                Boolean.class, fundId)).isTrue();
        assertThat(accountingRebuildService.rebuildIfPending()).isFalse();
    }

    private void insertNav(long fundId, String date, String nav, String accumulatedNav) {
        jdbcTemplate.update("insert into fund_nav_history(fund_id,nav_date,nav,accumulated_nav,version," +
                        "created_date,updated_date) values(?,?,?,?,0,now(),now())",
                fundId, Timestamp.from(Instant.parse(date)), new BigDecimal(nav), new BigDecimal(accumulatedNav));
    }

    private long insertTx(long fundId, String source, String amount, String shares, String nav,
                          String feeRate, String tradeDate) {
        return jdbcTemplate.queryForObject("insert into fund_transaction(fund_id,amount,shares,nav,fee_rate," +
                        "status,source,trade_date,confirm_time,version,created_date,updated_date) " +
                        "values(?,?,?,?,?,'CONFIRMED',?,?,?,0,now(),now()) returning id", Long.class,
                fundId, amount == null ? null : new BigDecimal(amount), new BigDecimal(shares), new BigDecimal(nav),
                new BigDecimal(feeRate), source, Timestamp.from(Instant.parse(tradeDate)),
                Timestamp.from(Instant.parse(tradeDate)));
    }

    private BigDecimal decimal(String sql, long id) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, id);
    }
}
