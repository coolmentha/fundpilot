package com.fundpilot.backend.alerting.application.command.rulemanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler.RuleInput;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler.RuleInput.ConditionInput;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler.RuleInput.TakeProfitInput;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionStateRepository;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.platform.web.error.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/** 提醒规则写操作的错误码映射与多用户隔离。 */
class AlertRuleCommandHandlerTest {

    private static final long OWNER = 3L;
    private static final long OTHER_OWNER = 4L;

    @Test
    void 创建全局规则成功落库() {
        var rules = mock(AlertRuleRepository.class);
        var facts = mock(AlertFundFactsGateway.class);
        var views = mock(AlertRuleQueryHandler.class);
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, facts, views, mock(SuggestionStateRepository.class));

        handler.create(OWNER, input("GLOBAL", null, "ALL", List.of(rise("0.05"))));

        verify(rules).save(any(AlertRule.class));
    }

    @Test
    void 创建单基金规则要求基金在关注列表() {
        var rules = mock(AlertRuleRepository.class);
        var facts = mock(AlertFundFactsGateway.class);
        var views = mock(AlertRuleQueryHandler.class);
        when(facts.currentFunds(OWNER)).thenReturn(List.of(fund(11L)));
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, facts, views, mock(SuggestionStateRepository.class));

        handler.create(OWNER, input("fUnd", 11L, null, List.of(rise("0.03"))));

        verify(rules).save(any(AlertRule.class));
    }

    @Test
    void 条件为空时默认全部满足且参数取指标默认值() {
        var rules = mock(AlertRuleRepository.class);
        var views = mock(AlertRuleQueryHandler.class);
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class), views,
                mock(SuggestionStateRepository.class));

        handler.create(OWNER, input("GLOBAL", null, null,
                List.of(new ConditionInput("PRICE_VS_MA", null, "below", null))));

        var captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(rules).save(captor.capture());
        var condition = captor.getValue().conditions().conditions().getFirst();
        assertThat(captor.getValue().conditions().match().name()).isEqualTo("ALL");
        assertThat(condition.indicator()).isEqualTo(IndicatorCode.PRICE_VS_MA);
        assertThat(condition.relation()).isEqualTo(ConditionRelation.BELOW);
        assertThat(condition.params()).containsEntry("window", 250);
        assertThat(condition.effectiveThreshold()).isEqualByComparingTo("0");
    }

    @Test
    void 不传种类时默认为条件提醒() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), mock(SuggestionStateRepository.class));

        handler.create(OWNER, input("GLOBAL", null, "ALL", List.of(rise("0.05"))));

        var captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(rules).save(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(AlertRuleKind.CONDITION);
        assertThat(captor.getValue().suggestion()).isFalse();
    }

    @Test
    void 创建回撤止盈规则落库六个参数() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), mock(SuggestionStateRepository.class));

        handler.create(OWNER, new RuleInput("GLOBAL", null, "TRAILING_STOP", "ALL", null, takeProfit(), true));

        var captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(rules).save(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(AlertRuleKind.TRAILING_STOP);
        assertThat(captor.getValue().suggestion()).isTrue();
        assertThat(captor.getValue().conditions()).isNull();
        assertThat(captor.getValue().takeProfit()).isEqualTo(new TakeProfitParams(new BigDecimal("0.15"),
                new BigDecimal("0.06"), new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("0.20"), 10));
    }

    @Test
    void 非法规则种类返回KindInvalid() {
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class),
                mock(AlertFundFactsGateway.class), mock(AlertRuleQueryHandler.class),
                mock(SuggestionStateRepository.class));

        assertThatThrownBy(() -> handler.create(OWNER,
                new RuleInput("GLOBAL", null, "MOON", "ALL", List.of(rise("0.05")), null, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_KIND_INVALID"));
    }

    @Test
    void 回撤止盈缺少参数或参数越界返回ParameterInvalid() {
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class),
                mock(AlertFundFactsGateway.class), mock(AlertRuleQueryHandler.class),
                mock(SuggestionStateRepository.class));

        assertThatThrownBy(() -> handler.create(OWNER,
                new RuleInput("GLOBAL", null, "TRAILING_STOP", "ALL", null, null, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_PARAMETER_INVALID"));
        assertThatThrownBy(() -> handler.create(OWNER, new RuleInput("GLOBAL", null, "TRAILING_STOP", "ALL", null,
                new TakeProfitInput(new BigDecimal("1.5"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                        new BigDecimal("0.50"), new BigDecimal("0.20"), 10), true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_PARAMETER_INVALID"));
    }

    @Test
    void 更新规则后清除旧的建议状态() {
        var rules = mock(AlertRuleRepository.class);
        var states = mock(SuggestionStateRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OWNER)));
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), states);

        handler.update(OWNER, 7L, input("GLOBAL", null, "ALL", List.of(rise("0.05"))));

        verify(states).deleteByRule(7L);
    }

    @ParameterizedTest
    @MethodSource("invalidConditionInputs")
    void 非法条件返回ConditionInvalid(RuleInput input) {
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class),
                mock(AlertFundFactsGateway.class), mock(AlertRuleQueryHandler.class),
                mock(SuggestionStateRepository.class));

        assertThatThrownBy(() -> handler.create(OWNER, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_CONDITION_INVALID"));
    }

    @ParameterizedTest
    @MethodSource("invalidScopeInputs")
    void 非法范围返回ScopeInvalid(RuleInput input) {
        var facts = mock(AlertFundFactsGateway.class);
        when(facts.currentFunds(OWNER)).thenReturn(List.of(fund(11L)));
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class), facts,
                mock(AlertRuleQueryHandler.class), mock(SuggestionStateRepository.class));

        assertThatThrownBy(() -> handler.create(OWNER, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_SCOPE_INVALID"));
    }

    @Test
    void 指定未关注基金返回TargetFundInvalid() {
        var facts = mock(AlertFundFactsGateway.class);
        when(facts.currentFunds(OWNER)).thenReturn(List.of(fund(11L)));
        var rules = mock(AlertRuleRepository.class);
        var handler = new AlertRuleCommandHandler(rules, facts, mock(AlertRuleQueryHandler.class),
                mock(SuggestionStateRepository.class));

        assertThatThrownBy(() -> handler.create(OWNER, input("FUND", 99L, "ALL", List.of(rise("0.05")))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_TARGET_FUND_INVALID"));
        verify(rules, never()).save(any(AlertRule.class));
    }

    @Test
    void 更新他人规则返回NotFound() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OTHER_OWNER)));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), mock(SuggestionStateRepository.class));

        assertThatThrownBy(() -> handler.update(OWNER, 7L, input("GLOBAL", null, "ALL", List.of(rise("0.05")))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_NOT_FOUND"));
        verify(rules, never()).save(any(AlertRule.class));
    }

    @Test
    void 删除他人规则返回NotFound() {
        var rules = mock(AlertRuleRepository.class);
        var states = mock(SuggestionStateRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OTHER_OWNER)));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), states);

        assertThatThrownBy(() -> handler.delete(OWNER, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_NOT_FOUND"));
        verify(rules, never()).softDelete(anyLong());
        verify(states, never()).deleteByRule(anyLong());
    }

    @Test
    void 删除本人规则同时清除建议状态() {
        var rules = mock(AlertRuleRepository.class);
        var states = mock(SuggestionStateRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OWNER)));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), states);

        handler.delete(OWNER, 7L);

        verify(states).deleteByRule(7L);
        verify(rules).softDelete(7L);
    }

    @Test
    void 启用本人规则成功() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OWNER)));
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class), mock(SuggestionStateRepository.class));

        handler.changeEnabled(OWNER, 7L, true);

        verify(rules).save(any(AlertRule.class));
    }

    private static Stream<Arguments> invalidConditionInputs() {
        return Stream.of(
                Arguments.of(input("GLOBAL", null, "ALL", null)),
                Arguments.of(input("GLOBAL", null, "ALL", List.of())),
                Arguments.of(input("GLOBAL", null, "ALL", Arrays.asList(rise("0.05"), null))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition(null, "ABOVE", "0.05")))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition("  ", "ABOVE", "0.05")))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition("MOON", "ABOVE", "0.05")))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition("DAILY_CHANGE", null, "0.05")))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition("DAILY_CHANGE", "MOON", "0.05")))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition("DAILY_CHANGE", "CROSS_ABOVE", "0.05")))),
                Arguments.of(input("GLOBAL", null, "ALL", List.of(condition("DAILY_CHANGE", "ABOVE", "5")))),
                Arguments.of(input("GLOBAL", null, "ALL",
                        List.of(condition("DAILY_CHANGE", "ABOVE", "0.05", Map.of("window", 20))))));
    }

    private static Stream<Arguments> invalidScopeInputs() {
        return Stream.of(
                Arguments.of(input("  ", null, "ALL", List.of(rise("0.05")))),
                Arguments.of(input("ALL", null, "ALL", List.of(rise("0.05")))),
                Arguments.of(input("GLOBAL", 11L, "ALL", List.of(rise("0.05")))),
                Arguments.of(input("FUND", null, "ALL", List.of(rise("0.05")))),
                Arguments.of(input("FUND", 0L, "ALL", List.of(rise("0.05")))));
    }

    /** 条件型规则的入参；不传种类时由服务端按条件提醒处理。 */
    private static RuleInput input(String scope, Long portfolioFundId, String match,
                                   List<ConditionInput> conditions) {
        return new RuleInput(scope, portfolioFundId, null, match, conditions, null, true);
    }

    private static TakeProfitInput takeProfit() {
        return new TakeProfitInput(new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10);
    }

    private static ConditionInput rise(String threshold) {
        return condition("DAILY_CHANGE", "ABOVE", threshold);
    }

    private static ConditionInput condition(String indicator, String relation, String value) {
        return new ConditionInput(indicator, Map.of(), relation, new BigDecimal(value));
    }

    private static ConditionInput condition(String indicator, String relation, String value,
                                            Map<String, Integer> params) {
        return new ConditionInput(indicator, params, relation, new BigDecimal(value));
    }

    private static AlertRule rule(long ownerId) {
        return AlertRule.rehydrate(7L, ownerId, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION,
                ConditionGroup.single(AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE,
                        new BigDecimal("0.05"))), null, true);
    }

    private static AlertFundFactsGateway.AlertFundFact fund(long portfolioFundId) {
        return new AlertFundFactsGateway.AlertFundFact(portfolioFundId, portfolioFundId + 100L, "161725",
                "招商中证白酒", "OPEN", true, "INDEX", Instant.parse("2026-01-05T00:00:00Z"),
                new BigDecimal("1.1"), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("0.05"), new BigDecimal("1000"), new BigDecimal("50"),
                new BigDecimal("0.05"), new BigDecimal("1.2"), new BigDecimal("1.2"), new BigDecimal("2.4"),
                Instant.parse("2026-09-19T00:00:00Z"), Instant.parse("2026-09-19T00:00:00Z"), "READY");
    }
}