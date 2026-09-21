package com.fundpilot.backend.alerting.application.command.rulemanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler.RuleInput;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import com.fundpilot.backend.platform.web.error.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        var handler = new AlertRuleCommandHandler(rules, facts, views);

        handler.create(OWNER, new RuleInput("GLOBAL", null, "RISE", new BigDecimal("0.05"), true));

        verify(rules).save(any(AlertRule.class));
    }

    @Test
    void 创建单基金规则要求基金在关注列表() {
        var rules = mock(AlertRuleRepository.class);
        var facts = mock(AlertFundFactsGateway.class);
        var views = mock(AlertRuleQueryHandler.class);
        when(facts.currentFunds(OWNER)).thenReturn(List.of(fund(11L)));
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, facts, views);

        handler.create(OWNER, new RuleInput("fUnd", 11L, "drop", new BigDecimal("0.03"), null));

        verify(rules).save(any(AlertRule.class));
    }

    @ParameterizedTest
    @MethodSource("invalidThresholdInputs")
    void 非法阈值返回ThresholdInvalid(RuleInput input) {
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class),
                mock(AlertFundFactsGateway.class), mock(AlertRuleQueryHandler.class));

        assertThatThrownBy(() -> handler.create(OWNER, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_THRESHOLD_INVALID"));
    }

    @ParameterizedTest
    @MethodSource("invalidScopeInputs")
    void 非法范围返回ScopeInvalid(RuleInput input) {
        var facts = mock(AlertFundFactsGateway.class);
        when(facts.currentFunds(OWNER)).thenReturn(List.of(fund(11L)));
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class), facts,
                mock(AlertRuleQueryHandler.class));

        assertThatThrownBy(() -> handler.create(OWNER, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_SCOPE_INVALID"));
    }

    @ParameterizedTest
    @MethodSource("invalidTypeInputs")
    void 非法类型返回TypeInvalid(RuleInput input) {
        var handler = new AlertRuleCommandHandler(mock(AlertRuleRepository.class),
                mock(AlertFundFactsGateway.class), mock(AlertRuleQueryHandler.class));

        assertThatThrownBy(() -> handler.create(OWNER, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_TYPE_INVALID"));
    }

    @Test
    void 指定未关注基金返回TargetFundInvalid() {
        var facts = mock(AlertFundFactsGateway.class);
        when(facts.currentFunds(OWNER)).thenReturn(List.of(fund(11L)));
        var rules = mock(AlertRuleRepository.class);
        var handler = new AlertRuleCommandHandler(rules, facts, mock(AlertRuleQueryHandler.class));

        assertThatThrownBy(() -> handler.create(OWNER,
                new RuleInput("FUND", 99L, "RISE", new BigDecimal("0.05"), true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_TARGET_FUND_INVALID"));
        verify(rules, never()).save(any(AlertRule.class));
    }

    @Test
    void 更新他人规则返回NotFound() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OTHER_OWNER)));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class));

        assertThatThrownBy(() -> handler.update(OWNER, 7L,
                new RuleInput("GLOBAL", null, "RISE", new BigDecimal("0.05"), true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_NOT_FOUND"));
        verify(rules, never()).save(any(AlertRule.class));
    }

    @Test
    void 删除他人规则返回NotFound() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OTHER_OWNER)));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class));

        assertThatThrownBy(() -> handler.delete(OWNER, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ALERT_RULE_NOT_FOUND"));
        verify(rules, never()).softDelete(any(Long.class));
    }

    @Test
    void 启用本人规则成功() {
        var rules = mock(AlertRuleRepository.class);
        when(rules.findById(7L)).thenReturn(Optional.of(rule(OWNER)));
        when(rules.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new AlertRuleCommandHandler(rules, mock(AlertFundFactsGateway.class),
                mock(AlertRuleQueryHandler.class));

        handler.changeEnabled(OWNER, 7L, true);

        verify(rules).save(any(AlertRule.class));
    }

    private static Stream<Arguments> invalidThresholdInputs() {
        return Stream.of(
                Arguments.of(new RuleInput("GLOBAL", null, "RISE", null, true)),
                Arguments.of(new RuleInput("GLOBAL", null, "RISE", BigDecimal.ZERO, true)),
                Arguments.of(new RuleInput("GLOBAL", null, "RISE", new BigDecimal("1.5"), true)));
    }

    private static Stream<Arguments> invalidScopeInputs() {
        return Stream.of(
                Arguments.of(new RuleInput("  ", null, "RISE", new BigDecimal("0.05"), true)),
                Arguments.of(new RuleInput("ALL", null, "RISE", new BigDecimal("0.05"), true)),
                Arguments.of(new RuleInput("GLOBAL", 11L, "RISE", new BigDecimal("0.05"), true)),
                Arguments.of(new RuleInput("FUND", null, "RISE", new BigDecimal("0.05"), true)),
                Arguments.of(new RuleInput("FUND", 0L, "RISE", new BigDecimal("0.05"), true)));
    }

    private static Stream<Arguments> invalidTypeInputs() {
        return Stream.of(
                Arguments.of(new RuleInput("GLOBAL", null, null, new BigDecimal("0.05"), true)),
                Arguments.of(new RuleInput("GLOBAL", null, "  ", new BigDecimal("0.05"), true)),
                Arguments.of(new RuleInput("GLOBAL", null, "MOON", new BigDecimal("0.05"), true)));
    }

    private static AlertRule rule(long ownerId) {
        return AlertRule.rehydrate(7L, ownerId, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE,
                new BigDecimal("0.05"), true);
    }

    private static AlertFundFactsGateway.AlertFundFact fund(long portfolioFundId) {
        return new AlertFundFactsGateway.AlertFundFact(portfolioFundId, "161725", "招商中证白酒", "OPEN", true,
                new BigDecimal("0.05"), new BigDecimal("1000"), new BigDecimal("50"),
                new BigDecimal("0.05"), new BigDecimal("1.2"), Instant.parse("2026-09-19T00:00:00Z"), "READY");
    }
}
