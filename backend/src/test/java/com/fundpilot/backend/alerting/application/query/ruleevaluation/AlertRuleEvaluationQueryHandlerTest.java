package com.fundpilot.backend.alerting.application.query.ruleevaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundpilot.backend.alerting.application.command.notificationdelivery.AlertNotificationDispatchCommandHandler;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler;
import com.fundpilot.backend.alerting.application.condition.ConditionEvaluationService;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertRecipientGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertIndicatorGateway;
import com.fundpilot.backend.alerting.application.suggestion.AlertSuggestionService;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationStatus;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionState;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionStateRepository;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 评估编排的行为约束：条件求值、范围优先级、幂等、失败隔离。 */
class AlertRuleEvaluationQueryHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T06:30:00Z");
    private static final long OWNER = 3L;
    private static final long OTHER_OWNER = 4L;

    private final InMemoryRules rules = new InMemoryRules();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final InMemoryStates states = new InMemoryStates();
    private final FakeFacts facts = new FakeFacts();
    private final FakeIndicators indicators = new FakeIndicators();
    private final FakeRecipients recipients = new FakeRecipients();
    private final RecordingMail mail = new RecordingMail();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    /** 把业务时钟往未来推若干自然日；测试里自然日即交易日。 */
    private void advance(int days) {
        clock = Clock.fixed(NOW.plus(Duration.ofDays(days)), ZoneOffset.UTC);
    }

    @Test
    void 非交易日不评估也不发送() {
        facts.tradingDay = false;
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isZero();
        assertThat(mail.messages).isEmpty();
        assertThat(notifications.store).isEmpty();
    }

    @Test
    void 全局规则命中多只基金合并为一封邮件() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"),
                fund(13L, "110022", true, "-0.05", "0.01"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isEqualTo(1);
        assertThat(result.matchedRules()).isEqualTo(1);
        assertThat(result.sentRules()).isEqualTo(1);
        assertThat(mail.messages).hasSize(1);
        assertThat(mail.messages.getFirst().funds())
                .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                .containsExactly(11L, 12L);
        assertThat(notifications.store).singleElement()
                .satisfies(record -> {
                    assertThat(record.status()).isEqualTo(AlertNotificationStatus.SENT);
                    assertThat(record.fundCount()).isEqualTo(2);
                    assertThat(record.recipientEmail()).isEqualTo("owner@example.com");
                    assertThat(record.sentAt()).isEqualTo(NOW);
                });
    }

    @Test
    void 多条件需全部满足才命中() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, ConditionGroup.allOf(List.of(
                AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE, new BigDecimal("0.02")),
                AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE, new BigDecimal("0.08"))))
                , true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.03", "0.05"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement().satisfies(message -> {
            assertThat(message.conditionSummary()).isEqualTo("当日涨跌幅 高于 0.02 且 持仓收益率 高于 0.08");
            assertThat(message.funds())
                    .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                    .containsExactly(11L);
            assertThat(message.funds().getFirst().conditionDetail())
                    .isEqualTo("当日涨跌幅 高于 0.02，现值 0.03，持仓收益率 高于 0.08，现值 0.1");
        });
        assertThat(notifications.store).singleElement().satisfies(record ->
                assertThat(record.triggerSummary()).isEqualTo(
                        "基金161725(161725) 命中：当日涨跌幅 高于 0.02，现值 0.03，持仓收益率 高于 0.08，现值 0.1"));
    }

    @Test
    void 行情数据缺失的条件不命中() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null,
                ConditionGroup.single(AlertCondition.of(IndicatorCode.PRICE_VS_MA, ConditionRelation.BELOW,
                        new BigDecimal("-0.05"))), true);
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.matchedRules()).isZero();
        assertThat(mail.messages).isEmpty();
    }

    @Test
    void 单基金规则只匹配自身基金() {
        rules.add(OWNER, AlertRuleScope.FUND, 12L, rise("0.01"), true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement().satisfies(message ->
                assertThat(message.funds())
                        .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                        .containsExactly(12L));
    }

    @Test
    void 盈利条件跳过非持仓基金() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, profit("0.05"), true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", false, "0.03", "0.20"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement().satisfies(message -> {
            assertThat(message.conditionSummary()).isEqualTo("持仓收益率 高于 0.05");
            assertThat(message.funds())
                    .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                    .containsExactly(11L);
        });
    }

    @Test
    void 同一规则当日已发送后跳过() {
        AlertRule rule = rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        notifications.insert(AlertNotification.rehydrate(1L, null, OWNER, rule.id(), null, null, null,
                BusinessDay.toDateLabel(NOW), AlertNotificationStatus.SENT, "owner@example.com", 1, "已发送",
                null, NOW));
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isEqualTo(1);
        assertThat(result.matchedRules()).isZero();
        assertThat(result.sentRules()).isZero();
        assertThat(mail.messages).isEmpty();
        assertThat(notifications.store).hasSize(1);
    }

    @Test
    void 未配置邮箱记失败并可下次重试() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));

        var result = handler().evaluate();

        assertThat(result.matchedRules()).isEqualTo(1);
        assertThat(result.sentRules()).isZero();
        assertThat(result.failedRules()).isEqualTo(1);
        assertThat(mail.messages).isEmpty();
        assertThat(notifications.store).singleElement().satisfies(record -> {
            assertThat(record.status()).isEqualTo(AlertNotificationStatus.FAILED);
            assertThat(record.failureReason()).isEqualTo("未配置提醒邮箱");
            assertThat(record.recipientEmail()).isNull();
            assertThat(record.conditionsSnapshot()).isNotNull();
        });

        recipients.emails.put(OWNER, "owner@example.com");
        assertThat(handler().evaluate().sentRules()).isEqualTo(1);
    }

    @Test
    void 单条规则异常不影响其它规则() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        rules.add(OTHER_OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));
        facts.funds(OTHER_OWNER, fund(21L, "000001", true, "0.03", "0.10"));
        recipients.emails.put(OWNER, "boom@example.com");
        recipients.emails.put(OTHER_OWNER, "fine@example.com");
        mail.exploding.add("boom@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isEqualTo(2);
        assertThat(result.sentRules()).isEqualTo(1);
        assertThat(result.failedRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement()
                .satisfies(message -> assertThat(message.recipient()).isEqualTo("fine@example.com"));
    }

    @Test
    void 同口径的单基金规则覆盖全局规则且不误伤其它基金() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, rise("0.50"), true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isEqualTo(2);
        assertThat(result.matchedRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement().satisfies(message ->
                assertThat(message.funds())
                        .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                        .containsExactly(12L));
    }

    @Test
    void 单基金规则与全局规则同时命中时各发一封() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, rise("0.01"), true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(2);
        assertThat(notifications.store).hasSize(2);
        assertThat(mail.messages).extracting(AlertEmailGateway.AlertEmailMessage::fundCount)
                .containsExactlyInAnyOrder(1, 1);
    }

    @Test
    void 单基金规则被禁用后回退全局规则() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, rise("0.50"), false);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement().satisfies(message ->
                assertThat(message.funds())
                        .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                        .containsExactly(11L, 12L));
    }

    @Test
    void 不同口径的单基金规则不覆盖全局规则() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, rise("0.01"), true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, profit("0.05"), true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.01"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(2);
        assertThat(mail.messages).extracting(AlertEmailGateway.AlertEmailMessage::conditionSummary)
                .containsExactlyInAnyOrder("当日涨跌幅 高于 0.01", "持仓收益率 高于 0.05");
        assertThat(mail.messages)
                .filteredOn(message -> message.conditionSummary().startsWith("当日涨跌幅"))
                .singleElement().satisfies(message -> assertThat(message.funds()).hasSize(2));
    }

    @Test
    void 试算命中时返回各条件现值且不落库不发送() {
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"));

        var result = handler().preview(OWNER, input("GLOBAL", null, "DAILY_CHANGE", "ABOVE", "0.01"));

        assertThat(result.hit()).isTrue();
        assertThat(result.tradingDate()).isEqualTo(BusinessDay.toDateLabel(NOW));
        assertThat(result.funds()).hasSize(2);
        assertThat(result.funds().getFirst().portfolioFundId()).isEqualTo(11L);
        assertThat(result.funds().getFirst().fundCode()).isEqualTo("161725");
        assertThat(result.funds().getFirst().conditions()).singleElement().satisfies(condition -> {
            assertThat(condition.text()).isEqualTo("当日涨跌幅 高于 0.01");
            assertThat(condition.currentValue()).isEqualByComparingTo("0.03");
            assertThat(condition.satisfied()).isTrue();
        });
        assertThat(mail.messages).isEmpty();
        assertThat(notifications.store).isEmpty();
    }

    @Test
    void 试算不命中时仍返回各条件现值() {
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));

        var result = handler().preview(OWNER, input("GLOBAL", null, "DAILY_CHANGE", "ABOVE", "0.05"));

        assertThat(result.hit()).isFalse();
        assertThat(result.funds().getFirst().hit()).isFalse();
        assertThat(result.funds().getFirst().conditions()).singleElement().satisfies(condition -> {
            assertThat(condition.currentValue()).isEqualByComparingTo("0.03");
            assertThat(condition.satisfied()).isFalse();
        });
    }

    @Test
    void 试算缺数据的条件现值与满足状态都为空与假() {
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));

        var result = handler().preview(OWNER, input("GLOBAL", null, "PRICE_VS_MA", "BELOW", "-0.05"));

        assertThat(result.hit()).isFalse();
        assertThat(result.funds().getFirst().conditions()).singleElement().satisfies(condition -> {
            assertThat(condition.currentValue()).isNull();
            assertThat(condition.satisfied()).isFalse();
        });
    }

    @Test
    void 单基金范围试算只返回该基金() {
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.05"));

        var result = handler().preview(OWNER, input("FUND", 12L, "DAILY_CHANGE", "ABOVE", "0.01"));

        assertThat(result.funds()).singleElement()
                .satisfies(preview -> assertThat(preview.portfolioFundId()).isEqualTo(12L));
    }

    @Test
    void 试算拒绝不属于当前用户的基金() {
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));

        assertThatThrownBy(() -> handler().preview(OWNER, input("FUND", 99L, "DAILY_CHANGE", "ABOVE", "0.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("指定基金不在当前关注列表中");
    }

    @Test
    void 试算拒绝未声明的指标() {
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));

        assertThatThrownBy(() -> handler().preview(OWNER, input("GLOBAL", null, "NOPE", "ABOVE", "0.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的提醒指标");
    }

    @Test
    void 逻辑破坏止损命中后给出全仓卖出建议() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleKind.LOGIC_BROKEN, rise("0.01"), null, true);
        facts.funds(OWNER, activeFund(11L, "0.03", "0.30"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(1);
        var row = mail.messages.getFirst().funds().getFirst();
        assertThat(row.suggestion()).isEqualTo("建议全部卖出 10000 份");
        assertThat(row.conditionDetail()).endsWith("主动型基金不适用量能条件");
    }

    @Test
    void 逻辑破坏止损连续命中只提醒一次() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleKind.LOGIC_BROKEN, rise("0.01"), null, true);
        facts.funds(OWNER, activeFund(11L, "0.03", "0.30"));
        recipients.emails.put(OWNER, "owner@example.com");

        assertThat(handler().evaluate().sentRules()).isEqualTo(1);
        notifications.store.clear();

        var second = handler().evaluate();

        assertThat(second.matchedRules()).isZero();
        assertThat(mail.messages).hasSize(1);
    }

    @Test
    void 非主动型基金缺少量能数据时不命中() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleKind.LOGIC_BROKEN, rise("0.01"), null, true);
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.30"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.matchedRules()).isZero();
        assertThat(mail.messages).isEmpty();
    }

    @Test
    void 回撤止盈收益率达标后累积峰值并在回撤达标时给出建议份额() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null, takeProfit(), true);
        recipients.emails.put(OWNER, "owner@example.com");
        facts.funds(OWNER, takeProfitFund("3.0"));

        assertThat(handler().evaluate().matchedRules()).isZero();

        notifications.store.clear();
        facts.funds(OWNER, takeProfitFund("2.8"));

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(1);
        var row = mail.messages.getFirst().funds().getFirst();
        assertThat(row.suggestion()).isEqualTo("建议卖出 1666.67 份（约占持仓 16.67%）");
        assertThat(row.conditionDetail()).startsWith("盈利 50% 后回撤 6.67%");
    }

    @Test
    void 回撤止盈触发后冷静期内不再提醒() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null, takeProfit(), true);
        recipients.emails.put(OWNER, "owner@example.com");
        facts.funds(OWNER, takeProfitFund("3.0"));

        assertThat(handler().evaluate().matchedRules()).isZero();

        advance(1);
        facts.funds(OWNER, takeProfitFund("2.8"));
        assertThat(handler().evaluate().sentRules()).isEqualTo(1);
        assertThat(mail.messages).hasSize(1);

        notifications.store.clear();
        advance(3);

        assertThat(handler().evaluate().matchedRules()).isZero();
        assertThat(mail.messages).hasSize(1);
    }

    @Test
    void 试算回撤止盈只回答当前收益率是否达门槛() {
        facts.funds(OWNER, takeProfitFund("3.0"));

        var result = handler().preview(OWNER, new AlertRuleCommandHandler.RuleInput("GLOBAL", null,
                "TRAILING_STOP", "ALL", null, new AlertRuleCommandHandler.RuleInput.TakeProfitInput(
                        new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.5"),
                        new BigDecimal("0.5"), new BigDecimal("0.2"), 10), true));

        assertThat(result.hit()).isTrue();
        assertThat(result.funds()).singleElement().satisfies(fund -> {
            assertThat(fund.conditions()).singleElement().satisfies(condition -> {
                assertThat(condition.text()).isEqualTo("持仓收益率 高于 0.1");
                assertThat(condition.currentValue()).isEqualByComparingTo("0.5");
                assertThat(condition.satisfied()).isTrue();
            });
        });
    }

    private static AlertRuleCommandHandler.RuleInput input(String scope, Long portfolioFundId, String indicator,
                                                           String relation, String value) {
        return new AlertRuleCommandHandler.RuleInput(scope, portfolioFundId, null, "ALL",
                List.of(new AlertRuleCommandHandler.RuleInput.ConditionInput(indicator, Map.of(), relation,
                        new BigDecimal(value))), null, true);
    }

    private AlertRuleEvaluationQueryHandler handler() {
        ConditionEvaluationService conditions = new ConditionEvaluationService(indicators);
        AlertSuggestionService suggestions = new AlertSuggestionService(facts, indicators, conditions, states);
        return new AlertRuleEvaluationQueryHandler(rules, notifications, facts, conditions, suggestions, recipients,
                new AlertNotificationDispatchCommandHandler(notifications, mail, clock), clock);
    }

    private static ConditionGroup rise(String threshold) {
        return ConditionGroup.single(AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE,
                new BigDecimal(threshold)));
    }

    private static ConditionGroup profit(String threshold) {
        return ConditionGroup.single(AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE,
                new BigDecimal(threshold)));
    }

    private static AlertFundFactsGateway.AlertFundFact fund(long portfolioFundId, String fundCode,
                                                            boolean open, String dailyChangePct,
                                                            String holdingReturnRate) {
        return new AlertFundFactsGateway.AlertFundFact(portfolioFundId, portfolioFundId + 100L, fundCode,
                "基金" + fundCode, open ? "OPEN" : "PENDING_HOLDING", open, "INDEX",
                NOW.minusSeconds(86400 * 30), new BigDecimal("1.0"), new BigDecimal("10000"),
                new BigDecimal("10000"), new BigDecimal(dailyChangePct), new BigDecimal("10000"),
                new BigDecimal("500"), new BigDecimal(holdingReturnRate), new BigDecimal("1.2345"),
                new BigDecimal("1.2345"), new BigDecimal("2.4690"), NOW.minusSeconds(86400 * 10),
                NOW, "REALTIME");
    }

    /** 主动型基金的持仓事实：productType 为 ACTIVE，量能条件不适用。 */
    private static AlertFundFactsGateway.AlertFundFact activeFund(long portfolioFundId, String dailyChangePct,
                                                                  String holdingReturnRate) {
        return new AlertFundFactsGateway.AlertFundFact(portfolioFundId, portfolioFundId + 100L, "161725",
                "基金161725", "OPEN", true, "ACTIVE", NOW.minusSeconds(86400 * 30),
                new BigDecimal("1.0"), new BigDecimal("10000"), new BigDecimal("10000"),
                new BigDecimal(dailyChangePct), new BigDecimal("10000"), new BigDecimal("500"),
                new BigDecimal(holdingReturnRate), new BigDecimal("1.2345"), new BigDecimal("1.2345"),
                new BigDecimal("2.4690"), NOW.minusSeconds(86400 * 30), NOW, "REALTIME");
    }

    /** 回撤止盈的持仓事实：成本 1.0、份额 10000、单位净值 1.5（收益率 50%），累计净值由参数给定。 */
    private static AlertFundFactsGateway.AlertFundFact takeProfitFund(String accumulatedNav) {
        return new AlertFundFactsGateway.AlertFundFact(11L, 111L, "161725", "基金161725", "OPEN", true,
                "INDEX", NOW.minusSeconds(86400 * 30), new BigDecimal("1.0"), new BigDecimal("10000"),
                new BigDecimal("10000"), new BigDecimal("0.01"), new BigDecimal("15000"),
                new BigDecimal("5000"), new BigDecimal("0.5"), new BigDecimal("1.5"),
                new BigDecimal("1.5"), new BigDecimal(accumulatedNav), NOW.minusSeconds(86400 * 30),
                NOW, "REALTIME");
    }

    private static TakeProfitParams takeProfit() {
        return new TakeProfitParams(new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.5"),
                new BigDecimal("0.5"), new BigDecimal("0.2"), 10);
    }

    private static final class InMemoryRules implements AlertRuleRepository {
        private final Map<Long, AlertRule> store = new LinkedHashMap<>();
        private long sequence;

        AlertRule add(long ownerId, AlertRuleScope scope, Long portfolioFundId, ConditionGroup conditions,
                      boolean enabled) {
            return add(ownerId, scope, portfolioFundId, AlertRuleKind.CONDITION, conditions, null, enabled);
        }

        AlertRule add(long ownerId, AlertRuleScope scope, Long portfolioFundId, AlertRuleKind kind,
                      ConditionGroup conditions, TakeProfitParams takeProfit, boolean enabled) {
            AlertRule rule = AlertRule.rehydrate(++sequence, ownerId, scope, portfolioFundId, kind, conditions,
                    takeProfit, enabled);
            store.put(rule.id(), rule);
            return rule;
        }

        @Override
        public Optional<AlertRule> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<AlertRule> findByOwnerId(long ownerId) {
            return store.values().stream().filter(rule -> rule.ownerId() == ownerId).toList();
        }

        @Override
        public List<AlertRule> findAllEnabled() {
            return store.values().stream().filter(AlertRule::enabled).toList();
        }

        @Override
        public AlertRule save(AlertRule rule) {
            store.put(rule.id(), rule);
            return rule;
        }

        @Override
        public void softDelete(long id) {
            store.remove(id);
        }
    }

    private static final class InMemoryNotifications implements AlertNotificationRepository {
        private final List<AlertNotification> store = new ArrayList<>();
        private long sequence;

        void insert(AlertNotification notification) {
            store.add(notification);
            sequence = Math.max(sequence, notification.id());
        }

        @Override
        public AlertNotification save(AlertNotification notification) {
            AlertNotification stored = AlertNotification.rehydrate(++sequence, notification.version(),
                    notification.ownerId(), notification.alertRuleId(), notification.triggerType(),
                    notification.threshold(), notification.conditionsSnapshot(),
                    notification.tradingDate(), notification.status(), notification.recipientEmail(),
                    notification.fundCount(), notification.triggerSummary(), notification.failureReason(),
                    notification.sentAt());
            store.add(stored);
            return stored;
        }

        @Override
        public int countSentByRuleAndTradingDate(long alertRuleId, Instant tradingDate) {
            return (int) store.stream()
                    .filter(record -> record.alertRuleId() == alertRuleId)
                    .filter(record -> record.status() == AlertNotificationStatus.SENT)
                    .filter(record -> utcDate(record.tradingDate()).equals(utcDate(tradingDate)))
                    .count();
        }

        @Override
        public List<AlertNotification> findLatestByOwnerId(long ownerId, int limit) {
            return store.stream()
                    .filter(record -> record.ownerId() == ownerId)
                    .sorted(Comparator.comparingLong(AlertNotification::id).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<RuleDailyStatus> findDailyStatusByOwnerId(long ownerId, Instant tradingDate) {
            Map<Long, List<AlertNotification>> grouped = store.stream()
                    .filter(record -> record.ownerId() == ownerId)
                    .filter(record -> record.status() == AlertNotificationStatus.SENT)
                    .filter(record -> utcDate(record.tradingDate()).equals(utcDate(tradingDate)))
                    .collect(java.util.stream.Collectors.groupingBy(AlertNotification::alertRuleId,
                            LinkedHashMap::new, java.util.stream.Collectors.toList()));
            return grouped.entrySet().stream()
                    .map(entry -> new RuleDailyStatus(entry.getKey(), entry.getValue().size(),
                            entry.getValue().stream().map(AlertNotification::sentAt)
                                    .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder())
                                    .orElse(null)))
                    .toList();
        }

        private static java.time.LocalDate utcDate(Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    private static final class FakeFacts implements AlertFundFactsGateway {
        private final Map<Long, List<AlertFundFact>> byOwner = new HashMap<>();
        private boolean tradingDay = true;

        void funds(long ownerId, AlertFundFact... funds) {
            byOwner.put(ownerId, List.of(funds));
        }

        @Override
        public List<AlertFundFact> currentFunds(long ownerId) {
            return byOwner.getOrDefault(ownerId, List.of());
        }

        @Override
        public boolean isTradingDay(Instant at) {
            return tradingDay;
        }

        /** 测试里把自然日差直接当作交易日数，够驱动最短持有与冷静期判定。 */
        @Override
        public long tradingDaysBetween(Instant fromExclusive, Instant toInclusive) {
            return java.time.Duration.between(fromExclusive, toInclusive).toDays();
        }
    }

    /** 建议型规则的状态存储；只按规则聚合，够驱动「连续命中只提醒一次」与「清仓后重置」。 */
    private static final class InMemoryStates implements SuggestionStateRepository {
        private final Map<Long, SuggestionState> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<SuggestionState> findByRuleAndFund(long alertRuleId, long portfolioFundId) {
            return store.values().stream()
                    .filter(state -> state.alertRuleId() == alertRuleId)
                    .filter(state -> state.portfolioFundId() == portfolioFundId)
                    .findFirst();
        }

        @Override
        public List<SuggestionState> findByRule(long alertRuleId) {
            return store.values().stream().filter(state -> state.alertRuleId() == alertRuleId).toList();
        }

        @Override
        public SuggestionState save(SuggestionState state) {
            SuggestionState stored = state.id() != null ? state
                    : SuggestionState.rehydrate(++sequence, state.alertRuleId(), state.ownerId(),
                            state.portfolioFundId(), state.phase(), state.cycleStartedAt(), state.cyclePeakNav(),
                            state.cooldownStartedAt());
            store.put(stored.id(), stored);
            return stored;
        }

        @Override
        public void deleteByRule(long alertRuleId) {
            store.values().removeIf(state -> state.alertRuleId() == alertRuleId);
        }
    }

    /** 行情类指标一律无数据，只保留基金事实类的两个口径，便于在无数据库前提下驱动评估。 */
    private static final class FakeIndicators implements AlertIndicatorGateway {
        @Override
        public List<BigDecimal> values(AlertFundFactsGateway.AlertFundFact fund, AlertCondition condition,
                                       Instant endExclusive) {
            BigDecimal value = switch (condition.indicator()) {
                case DAILY_CHANGE -> fund.dailyChangePct();
                case HOLDING_RETURN -> fund.open() ? fund.holdingReturnRate() : null;
                default -> null;
            };
            return value == null ? List.of() : List.of(value);
        }
    }

    private static final class FakeRecipients implements AlertRecipientGateway {
        private final Map<Long, String> emails = new HashMap<>();

        @Override
        public Optional<String> emailOf(long ownerId) {
            return Optional.ofNullable(emails.get(ownerId));
        }
    }

    private static final class RecordingMail implements AlertEmailGateway {
        private final List<AlertEmailMessage> messages = new ArrayList<>();
        private final Set<String> exploding = new HashSet<>();

        @Override
        public DeliveryResult send(AlertEmailMessage message) {
            if (exploding.contains(message.recipient())) {
                throw new IllegalStateException("SMTP 不可用");
            }
            messages.add(message);
            return DeliveryResult.success();
        }
    }
}