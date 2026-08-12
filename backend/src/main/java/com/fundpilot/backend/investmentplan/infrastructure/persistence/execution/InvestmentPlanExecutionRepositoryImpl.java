package com.fundpilot.backend.investmentplan.infrastructure.persistence.execution;

import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecution;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class InvestmentPlanExecutionRepositoryImpl implements InvestmentPlanExecutionRepository {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<InvestmentPlanExecution> find(long planId, Instant businessDate) {
        List<InvestmentPlanExecution> values = jdbc.query("""
                SELECT id, investment_plan_id, business_date, amount_strategy, rule_version, result,
                       reason_code, reason, base_amount, actual_amount, deduction_rate, data_date,
                       reference_index_code, moving_average_days, primary_metric, secondary_metric
                FROM investment_plan_execution
                WHERE investment_plan_id = ? AND business_date = ?
                """, this::map, planId, Timestamp.from(businessDate));
        return values.stream().findFirst();
    }

    @Override
    public boolean existsBetween(long planId, Instant startInclusive, Instant endExclusive) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM investment_plan_execution
                WHERE investment_plan_id = ? AND business_date >= ? AND business_date < ?
                """, Integer.class, planId, Timestamp.from(startInclusive), Timestamp.from(endExclusive));
        return count != null && count > 0;
    }

    @Override
    public List<InvestmentPlanExecution> findLatestByPlanIds(List<Long> planIds) {
        if (planIds.isEmpty()) return List.of();
        String placeholders = String.join(",", planIds.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT execution.id, execution.investment_plan_id, execution.business_date,
                       execution.amount_strategy, execution.rule_version, execution.result,
                       execution.reason_code, execution.reason, execution.base_amount,
                       execution.actual_amount, execution.deduction_rate, execution.data_date,
                       execution.reference_index_code, execution.moving_average_days,
                       execution.primary_metric, execution.secondary_metric
                FROM investment_plan_execution execution
                JOIN (SELECT investment_plan_id, max(business_date) AS latest_date
                      FROM investment_plan_execution WHERE investment_plan_id IN (%s)
                      GROUP BY investment_plan_id) latest
                  ON latest.investment_plan_id = execution.investment_plan_id
                 AND latest.latest_date = execution.business_date
                """.formatted(placeholders);
        return jdbc.query(sql, this::map, planIds.toArray());
    }

    @Override
    public List<InvestmentPlanExecution> findBetween(List<Long> planIds, Instant startInclusive, Instant endExclusive) {
        if (planIds.isEmpty()) return List.of();
        String placeholders = String.join(",", planIds.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT id, investment_plan_id, business_date, amount_strategy, rule_version, result,
                       reason_code, reason, base_amount, actual_amount, deduction_rate, data_date,
                       reference_index_code, moving_average_days, primary_metric, secondary_metric
                FROM investment_plan_execution
                WHERE investment_plan_id IN (%s) AND business_date >= ? AND business_date < ?
                """.formatted(placeholders);
        List<Object> args = new ArrayList<>(planIds);
        args.add(Timestamp.from(startInclusive));
        args.add(Timestamp.from(endExclusive));
        return jdbc.query(sql, this::map, args.toArray());
    }

    @Override
    public boolean insert(InvestmentPlanExecution execution) {
        int changed = jdbc.update("""
                INSERT INTO investment_plan_execution
                    (investment_plan_id, business_date, amount_strategy, rule_version, result,
                     reason_code, reason, base_amount, actual_amount, deduction_rate, data_date,
                     reference_index_code, moving_average_days, primary_metric, secondary_metric,
                     version, created_date, updated_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (investment_plan_id, business_date) DO NOTHING
                """, execution.planId(), Timestamp.from(execution.businessDate()), execution.amountStrategy().name(),
                execution.ruleVersion(), execution.result().name(), execution.reasonCode(), execution.reason(),
                execution.baseAmount(), execution.actualAmount(), execution.deductionRate(),
                timestamp(execution.dataDate()), execution.referenceIndexCode(), execution.movingAverageDays(),
                execution.primaryMetric(), execution.secondaryMetric());
        return changed > 0;
    }

    private InvestmentPlanExecution map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new InvestmentPlanExecution(rs.getLong("id"), rs.getLong("investment_plan_id"),
                rs.getTimestamp("business_date").toInstant(),
                InvestmentPlanAmountStrategy.valueOf(rs.getString("amount_strategy")),
                rs.getString("rule_version"), InvestmentPlanExecution.Result.valueOf(rs.getString("result")),
                rs.getString("reason_code"), rs.getString("reason"), rs.getBigDecimal("base_amount"),
                rs.getBigDecimal("actual_amount"), rs.getBigDecimal("deduction_rate"),
                instant(rs.getTimestamp("data_date")), rs.getString("reference_index_code"),
                rs.getObject("moving_average_days", Integer.class), rs.getBigDecimal("primary_metric"),
                rs.getBigDecimal("secondary_metric"));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
