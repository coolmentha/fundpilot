package com.fundpilot.backend.alerting.application.condition;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertIndicatorGateway;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionEvaluator;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 逐条条件的求值与中文说明。
 *
 * <p>条件型规则的命中判定、建议型规则里的条件部分、以及保存前试算共用同一份口径，避免三处各写一遍取值与比较。
 */
@Service
@RequiredArgsConstructor
public class ConditionEvaluationService {

    private static final String DETAIL_JOINER = "，";

    private final AlertIndicatorGateway indicators;

    /** 该基金上逐条条件的取值与满足情况；数据不足的指标取值为空、按不满足处理。 */
    public List<Row> evaluate(AlertFundFactsGateway.AlertFundFact fund, ConditionGroup group, Instant endExclusive) {
        List<Row> rows = new ArrayList<>(group.conditions().size());
        for (AlertCondition condition : group.conditions()) {
            List<BigDecimal> values = indicators.values(fund, condition, endExclusive);
            rows.add(new Row(condition, latest(values), ConditionEvaluator.satisfied(condition, values)));
        }
        return rows;
    }

    /** 全部条件是否同时满足；当前组合逻辑只做 AND。 */
    public static boolean satisfied(List<Row> rows) {
        return rows.stream().allMatch(Row::satisfied);
    }

    /** 命中说明：逐条条件的口径 + 现值，用中文逗号连接。 */
    public static String detail(List<Row> rows) {
        return rows.stream().map(Row::detail).collect(Collectors.joining(DETAIL_JOINER));
    }

    private static BigDecimal latest(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.getLast();
    }

    /** 一条条件在该基金上的当日取值与满足情况。 */
    public record Row(AlertCondition condition, BigDecimal latest, boolean satisfied) {

        /** 命中说明：口径 + 现值，作为邮件与该次提醒记录的内容。 */
        public String detail() {
            return AlertConditionText.describe(condition, latest);
        }

        /** 条件的中文口径，试算时与现值分列展示。 */
        public String summary() {
            return AlertConditionText.summarize(condition);
        }
    }
}