package com.fundpilot.backend.fund.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** 一次性重放已确认交易，重建单位净值口径下的交易派生账本。 */
@Service
@RequiredArgsConstructor
public class AccountingRebuildService {

    static final String REBUILD_KEY = "UNIT_NAV_V1";
    private static final Logger log = LoggerFactory.getLogger(AccountingRebuildService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final ZoneId TRADING_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean rebuildIfPending() {
        List<String> statuses = jdbcTemplate.query(
                "select status from accounting_rebuild_state where rebuild_key = ? for update",
                (rs, rowNum) -> rs.getString(1), REBUILD_KEY);
        if (statuses.isEmpty() || "COMPLETED".equals(statuses.get(0))) {
            return false;
        }

        Map<Long, OldLotEvidence> oldLots = snapshotLotEvidence();
        Map<Long, BigDecimal> onboardingCosts = inferOnboardingCosts();
        Map<Long, ArrayDeque<BigDecimal>> oldRedemptionRates = snapshotRedemptionRates();
        Map<Long, NavigableMap<LocalDate, BigDecimal>> unitNavs = loadUnitNavs();
        List<TransactionRow> transactions = loadConfirmedTransactions();

        jdbcTemplate.update("delete from fund_lot_redemption");
        jdbcTemplate.update("delete from fund_lot");

        Map<Long, ArrayDeque<ReplayLot>> lotsByFund = new HashMap<>();
        Map<Long, BigDecimal> transferNetAmounts = new HashMap<>();

        for (TransactionRow tx : transactions) {
            BigDecimal unitNav = requiresNav(tx.source()) ? requireUnitNav(unitNavs, tx) : tx.nav();
            switch (tx.source()) {
                case "INCREASE", "INVEST", "TRANSFER_IN" -> replayBuy(
                        tx, unitNav, oldLots, onboardingCosts, lotsByFund, transferNetAmounts);
                case "DECREASE", "TRANSFER_OUT" -> replaySell(
                        tx, unitNav, lotsByFund, transferNetAmounts, oldRedemptionRates);
                case "ADJUST_OUT" -> consumeLots(tx, lotsByFund, false, BigDecimal.ZERO, unitNav);
                case "ADJUST_IN" -> { /* 事实份额由交易保留，不创建收费 lot。 */ }
                default -> throw new IllegalStateException("未知交易来源: " + tx.source());
            }
        }

        jdbcTemplate.update("update fund f set cost_per_share = lots.cost_per_share, updated_date = now() " +
                "from (select fund_id, sum(remaining_shares * acquire_cost_per_share) / " +
                "nullif(sum(remaining_shares), 0) cost_per_share from fund_lot " +
                "where deleted_date is null and remaining_shares > 0 group by fund_id) lots " +
                "where f.id = lots.fund_id");
        jdbcTemplate.update("update fund_strategy set take_profit_phase = 'ACCUMULATING', " +
                "cycle_started_at = null, cycle_peak_nav = null, triggered_signal_id = null, " +
                "cooldown_started_at = null, updated_date = now() " +
                "where status = 'EFFECTIVE' and deleted_date is null");
        jdbcTemplate.update("update accounting_rebuild_state set status = 'COMPLETED', " +
                "completed_at = now(), details = ? where rebuild_key = ?",
                "Rebuilt " + transactions.size() + " confirmed transactions", REBUILD_KEY);
        log.info("单位净值历史账本重建完成 transactions={}", transactions.size());
        return true;
    }

    private void replayBuy(TransactionRow tx, BigDecimal unitNav, Map<Long, OldLotEvidence> oldLots,
                           Map<Long, BigDecimal> onboardingCosts,
                           Map<Long, ArrayDeque<ReplayLot>> lotsByFund,
                           Map<Long, BigDecimal> transferNetAmounts) {
        BigDecimal amount = tx.amount();
        if ("TRANSFER_IN".equals(tx.source()) && tx.relatedTxId() != null) {
            amount = transferNetAmounts.getOrDefault(tx.relatedTxId(), amount);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("买入交易缺少有效金额 tx=" + tx.id());
        }
        BigDecimal rate = purchaseRate(tx, amount);
        BigDecimal fee = amount.multiply(rate, MATH);
        OldLotEvidence oldLot = oldLots.get(tx.id());
        BigDecimal onboardingCost = onboardingCosts.get(tx.id());
        BigDecimal shares = onboardingCost != null
                ? ShareScale.normalize(tx.shares())
                : ShareScale.normalize(amount.subtract(fee).divide(unitNav, MATH));
        BigDecimal ordinaryCost = amount.divide(shares, MATH);
        BigDecimal costPerShare = onboardingCost != null ? onboardingCost : ordinaryCost;
        Instant acquireTime = onboardingCost != null && oldLot != null ? oldLot.acquireTime() : tx.tradeTime();

        jdbcTemplate.update("update fund_transaction set amount=?, shares=?, nav=?, fee=?, fee_rate=?, " +
                        "updated_date=now() where id=?",
                amount, shares, unitNav, fee, rate.signum() > 0 ? rate : null, tx.id());
        long lotId = jdbcTemplate.queryForObject(
                "insert into fund_lot(fund_id, acquire_tx_id, acquire_date, acquire_shares, remaining_shares, " +
                        "acquire_cost_per_share, version, created_date, updated_date) " +
                        "values(?,?,?,?,?,?,0,now(),now()) returning id", Long.class,
                tx.fundId(), tx.id(), Timestamp.from(acquireTime), shares, shares, costPerShare);
        lotsByFund.computeIfAbsent(tx.fundId(), ignored -> new ArrayDeque<>())
                .add(new ReplayLot(lotId, acquireTime, shares));
    }

    private void replaySell(TransactionRow tx, BigDecimal unitNav,
                            Map<Long, ArrayDeque<ReplayLot>> lotsByFund,
                            Map<Long, BigDecimal> transferNetAmounts,
                            Map<Long, ArrayDeque<BigDecimal>> oldRedemptionRates) {
        BigDecimal rate = redemptionRate(tx, unitNav);
        BigDecimal totalFee = consumeLots(tx, lotsByFund, true, rate, unitNav,
                oldRedemptionRates.get(tx.id()));
        BigDecimal gross = tx.shares().multiply(unitNav, MATH);
        BigDecimal net = gross.subtract(totalFee);
        jdbcTemplate.update("update fund_transaction set amount=?, nav=?, fee=?, fee_rate=?, updated_date=now() where id=?",
                net, unitNav, totalFee, gross.signum() > 0 ? totalFee.divide(gross, MATH) : null, tx.id());
        transferNetAmounts.put(tx.id(), net);
    }

    private BigDecimal consumeLots(TransactionRow tx, Map<Long, ArrayDeque<ReplayLot>> lotsByFund,
                                   boolean recordRedemption, BigDecimal rate, BigDecimal unitNav) {
        return consumeLots(tx, lotsByFund, recordRedemption, rate, unitNav, null);
    }

    private BigDecimal consumeLots(TransactionRow tx, Map<Long, ArrayDeque<ReplayLot>> lotsByFund,
                                   boolean recordRedemption, BigDecimal fallbackRate, BigDecimal unitNav,
                                   ArrayDeque<BigDecimal> historicalRates) {
        BigDecimal remaining = tx.shares();
        BigDecimal totalFee = BigDecimal.ZERO;
        ArrayDeque<ReplayLot> lots = lotsByFund.computeIfAbsent(tx.fundId(), ignored -> new ArrayDeque<>());
        while (remaining.signum() > 0 && !lots.isEmpty()) {
            ReplayLot lot = lots.peek();
            BigDecimal consumed = remaining.min(lot.remaining);
            lot.remaining = lot.remaining.subtract(consumed);
            remaining = remaining.subtract(consumed);
            jdbcTemplate.update("update fund_lot set remaining_shares=?, updated_date=now() where id=?",
                    lot.remaining, lot.id);
            if (recordRedemption) {
                BigDecimal rate = historicalRates != null && !historicalRates.isEmpty()
                        ? historicalRates.remove() : fallbackRate;
                int holdingDays = (int) ChronoUnit.DAYS.between(
                        lot.acquireTime.atZone(TRADING_ZONE).toLocalDate(),
                        tx.tradeTime().atZone(TRADING_ZONE).toLocalDate());
                jdbcTemplate.update("insert into fund_lot_redemption(lot_id,sell_tx_id,shares_consumed," +
                                "holding_days,redemption_rate,version,created_date,updated_date) " +
                                "values(?,?,?,?,?,0,now(),now())",
                        lot.id, tx.id(), consumed, holdingDays, rate);
                totalFee = totalFee.add(consumed.multiply(unitNav, MATH).multiply(rate, MATH));
            }
            if (lot.remaining.signum() == 0) {
                lots.remove();
            }
        }
        if (remaining.signum() > 0) {
            log.warn("历史交易存在未跟踪调整份额 tx={} unmatched={},按零费率保留", tx.id(), remaining);
        }
        return totalFee;
    }

    private Map<Long, OldLotEvidence> snapshotLotEvidence() {
        Map<Long, OldLotEvidence> result = new HashMap<>();
        jdbcTemplate.query("select acquire_tx_id, acquire_date, acquire_cost_per_share " +
                        "from fund_lot where deleted_date is null",
                (RowCallbackHandler) rs -> result.put(rs.getLong(1),
                        new OldLotEvidence(rs.getTimestamp(2).toInstant(), rs.getBigDecimal(3))));
        return result;
    }

    private Map<Long, BigDecimal> inferOnboardingCosts() {
        Map<Long, BigDecimal> result = new HashMap<>();
        jdbcTemplate.query("with evidence as (" +
                        "select l.acquire_tx_id,l.fund_id,l.remaining_shares,l.acquire_cost_per_share," +
                        "f.cost_per_share, " +
                        "(t.source='INCREASE' and t.signal_log_id is null and t.dca_plan_id is null " +
                        "and l.acquire_date <> coalesce(t.trade_date,t.created_date,t.confirm_time)) onboarding " +
                        "from fund_lot l join fund_transaction t on t.id=l.acquire_tx_id " +
                        "join fund f on f.id=l.fund_id where l.deleted_date is null), totals as (" +
                        "select *,sum(remaining_shares) over(partition by fund_id) total_shares," +
                        "sum(case when not onboarding then remaining_shares*acquire_cost_per_share else 0 end) " +
                        "over(partition by fund_id) ordinary_cost," +
                        "sum(case when onboarding then remaining_shares else 0 end) " +
                        "over(partition by fund_id) onboarding_shares from evidence) " +
                        "select acquire_tx_id,(cost_per_share*total_shares-ordinary_cost)/nullif(onboarding_shares,0) " +
                        "from totals where onboarding",
                (RowCallbackHandler) rs -> result.put(rs.getLong(1), rs.getBigDecimal(2)));
        return result;
    }

    private Map<Long, ArrayDeque<BigDecimal>> snapshotRedemptionRates() {
        Map<Long, ArrayDeque<BigDecimal>> result = new HashMap<>();
        jdbcTemplate.query("select sell_tx_id, redemption_rate from fund_lot_redemption " +
                        "where deleted_date is null order by sell_tx_id, id",
                (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong(1), ignored -> new ArrayDeque<>())
                        .add(rs.getBigDecimal(2)));
        return result;
    }

    private Map<Long, NavigableMap<LocalDate, BigDecimal>> loadUnitNavs() {
        Map<Long, NavigableMap<LocalDate, BigDecimal>> result = new HashMap<>();
        jdbcTemplate.query("select fund_id, nav_date, nav from fund_nav_history " +
                        "where deleted_date is null and nav is not null",
                (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong(1), ignored -> new TreeMap<>())
                        .put(rs.getTimestamp(2).toInstant().atZone(TRADING_ZONE).toLocalDate(), rs.getBigDecimal(3)));
        return result;
    }

    private List<TransactionRow> loadConfirmedTransactions() {
        return jdbcTemplate.query("select id,fund_id,source,amount,shares,nav,fee,fee_rate," +
                        "coalesce(trade_date,created_date,confirm_time),related_fund_transaction_id,signal_log_id,dca_plan_id " +
                        "from fund_transaction where status='CONFIRMED' and deleted_date is null " +
                        "order by coalesce(trade_date,created_date,confirm_time),id",
                (rs, rowNum) -> new TransactionRow(rs.getLong("id"), rs.getLong("fund_id"),
                        rs.getString("source"), rs.getBigDecimal("amount"), rs.getBigDecimal("shares"),
                        rs.getBigDecimal("nav"), rs.getBigDecimal("fee"), rs.getBigDecimal("fee_rate"),
                        rs.getTimestamp(9).toInstant(), (Long) rs.getObject(10),
                        (Long) rs.getObject(11), (Long) rs.getObject(12)));
    }

    private BigDecimal requireUnitNav(Map<Long, NavigableMap<LocalDate, BigDecimal>> navs, TransactionRow tx) {
        NavigableMap<LocalDate, BigDecimal> fundNavs = navs.get(tx.fundId());
        LocalDate tradeDate = tx.tradeTime().atZone(TRADING_ZONE).toLocalDate();
        Map.Entry<LocalDate, BigDecimal> entry = fundNavs != null ? fundNavs.floorEntry(tradeDate) : null;
        BigDecimal nav = entry != null ? entry.getValue() : null;
        if (nav == null || nav.signum() <= 0) {
            throw new IllegalStateException("历史交易缺少单位净值 tx=" + tx.id() + " fund=" + tx.fundId());
        }
        if (!entry.getKey().equals(tradeDate)) {
            log.info("历史交易日无净值，使用此前最近交易日 tx={} trade_date={} nav_date={}",
                    tx.id(), tradeDate, entry.getKey());
        }
        return nav;
    }

    private BigDecimal purchaseRate(TransactionRow tx, BigDecimal amount) {
        if (tx.feeRate() != null) {
            return tx.feeRate();
        }
        if (tx.fee() != null && amount.signum() > 0) {
            return tx.fee().divide(amount, MATH);
        }
        log.warn("历史买入缺少手续费证据 tx={},按零费率重建", tx.id());
        return BigDecimal.ZERO;
    }

    private BigDecimal redemptionRate(TransactionRow tx, BigDecimal unitNav) {
        if (tx.feeRate() != null) {
            return tx.feeRate();
        }
        BigDecimal gross = tx.shares() != null ? tx.shares().multiply(unitNav, MATH) : BigDecimal.ZERO;
        if (tx.fee() != null && gross.signum() > 0) {
            return tx.fee().divide(gross, MATH);
        }
        log.warn("历史卖出缺少手续费证据 tx={},按零费率重建", tx.id());
        return BigDecimal.ZERO;
    }

    private boolean requiresNav(String source) {
        return !"ADJUST_IN".equals(source) && !"ADJUST_OUT".equals(source);
    }

    private record OldLotEvidence(Instant acquireTime, BigDecimal costPerShare) {}
    private record TransactionRow(Long id, Long fundId, String source, BigDecimal amount, BigDecimal shares,
                                  BigDecimal nav, BigDecimal fee, BigDecimal feeRate, Instant tradeTime,
                                  Long relatedTxId, Long signalLogId, Long dcaPlanId) {}
    private static final class ReplayLot {
        private final long id;
        private final Instant acquireTime;
        private BigDecimal remaining;
        private ReplayLot(long id, Instant acquireTime, BigDecimal remaining) {
            this.id = id;
            this.acquireTime = acquireTime;
            this.remaining = remaining;
        }
    }
}
