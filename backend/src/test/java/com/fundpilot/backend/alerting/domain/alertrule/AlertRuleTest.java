package com.fundpilot.backend.alerting.domain.alertrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.grid.GridParams;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AlertRuleTest {

    @Test
    void 全局规则忽略传入的基金ID() {
        AlertRule rule = AlertRule.create(3L, AlertRuleScope.GLOBAL, 11L, AlertRuleKind.CONDITION, rise("0.05"), null,
                true);

        assertThat(rule.global()).isTrue();
        assertThat(rule.portfolioFundId()).isNull();
        assertThat(rule.scope()).isEqualTo(AlertRuleScope.GLOBAL);
    }

    @Test
    void 单基金规则必须指定基金() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.FUND, null, AlertRuleKind.CONDITION, rise("0.05"), null, true));
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.FUND, 0L, AlertRuleKind.CONDITION, rise("0.05"), null, true));
    }

    @Test
    void 条件型规则的条件不能为空() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION, null, null, true));
    }

    @Test
    void 创建时非法用户ID被拒绝() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(0L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION, rise("0.05"), null, true));
    }

    @Test
    void 更新可切换范围与条件并支持启停() {
        AlertRule rule = AlertRule.rehydrate(9L, 3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION,
                rise("0.05"), null, true);

        rule.update(AlertRuleScope.FUND, 11L, AlertRuleKind.CONDITION, profit("0.20"), null);
        assertThat(rule.scope()).isEqualTo(AlertRuleScope.FUND);
        assertThat(rule.portfolioFundId()).isEqualTo(11L);
        assertThat(rule.conditions()).isEqualTo(profit("0.20"));

        rule.disable();
        assertThat(rule.enabled()).isFalse();
        rule.enable();
        assertThat(rule.enabled()).isTrue();
    }

    @Test
    void 逻辑破坏止损需要配置条件且不接受止盈参数() {
        AlertRule rule = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.LOGIC_BROKEN,
                logicBroken(), null, true);

        assertThat(rule.suggestion()).isTrue();
        assertThat(rule.kind()).isEqualTo(AlertRuleKind.LOGIC_BROKEN);
        assertThat(rule.takeProfit()).isNull();

        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.LOGIC_BROKEN, null, null, true));
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.LOGIC_BROKEN, logicBroken(), params(),
                        true));
    }

    @Test
    void 回撤止盈必须配置止盈参数且不得携带条件() {
        AlertRule rule = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null,
                params(), true);
        assertThat(rule.takeProfit()).isEqualTo(params());

        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null, null, true));
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, rise("0.05"), params(),
                        true));
    }

    @Test
    void 条件型规则不接受止盈参数() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION, rise("0.05"), params(),
                        true));
    }

    @Test
    void 口径签名随种类与判定配置变化() {
        AlertRule first = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION, rise("0.05"),
                null, true);
        AlertRule same = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION, rise("0.05"),
                null, true);
        AlertRule different = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION,
                profit("0.05"), null, true);
        AlertRule takeProfit = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null,
                params(), true);

        assertThat(first.signature()).isEqualTo(same.signature());
        assertThat(first.signature()).isNotEqualTo(different.signature());
        assertThat(first.signature()).isNotEqualTo(takeProfit.signature());
    }

    @Test
    void 网格规则必须配置两个比例且不得携带条件或止盈参数() {
        GridParams params = grid("0.05", "0.03");
        AlertRule rule = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, null, null, params,
                true);
        assertThat(rule.grid()).isEqualTo(params);
        assertThat(rule.takeProfit()).isNull();
        assertThat(rule.conditions()).isNull();

        // 缺参数
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, null, null, null, true));
        // 网格不接受条件
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, rise("0.05"), null, params,
                        true));
        // 网格不接受止盈参数
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, null, params(), params, true));
    }

    @Test
    void 非网格规则不接受网格参数() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION, rise("0.05"), null,
                        grid("0.05", "0.03"), true));
        assertThatIllegalArgumentException().isThrownBy(() ->
                AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null, params(),
                        grid("0.05", "0.03"), true));
    }

    @Test
    void 网格规则是建议型且不需要条件() {
        assertThat(AlertRuleKind.GRID.suggestion()).isTrue();
        assertThat(AlertRuleKind.GRID.needsConditions()).isFalse();
    }

    @Test
    void 网格口径签名随比例变化且区别于同种类的其它参数() {
        AlertRule first = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, null, null,
                grid("0.05", "0.03"), true);
        AlertRule same = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, null, null,
                grid("0.05", "0.03"), true);
        AlertRule different = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleKind.GRID, null, null,
                grid("0.05", "0.04"), true);

        assertThat(first.signature()).isEqualTo(same.signature());
        assertThat(first.signature()).isNotEqualTo(different.signature());
    }

    private static GridParams grid(String upRatio, String downRatio) {
        return new GridParams(new BigDecimal(upRatio), new BigDecimal(downRatio));
    }

    private static ConditionGroup rise(String threshold) {
        return ConditionGroup.single(AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE,
                new BigDecimal(threshold)));
    }

    private static ConditionGroup profit(String threshold) {
        return ConditionGroup.single(AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE,
                new BigDecimal(threshold)));
    }

    private static ConditionGroup logicBroken() {
        return ConditionGroup.allOf(java.util.List.of(
                AlertCondition.of(IndicatorCode.PRICE_VS_MA, ConditionRelation.BELOW, BigDecimal.ZERO),
                AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM, ConditionRelation.DECREASING)));
    }

    private static TakeProfitParams params() {
        return new TakeProfitParams(new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10);
    }
}