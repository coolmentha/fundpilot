package com.fundpilot.backend.alerting.application.query.ruleevaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.alerting.application.command.notificationdelivery.AlertNotificationDispatchCommandHandler;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertRecipientGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationStatus;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Clock;
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

/** 评估编排的行为约束：范围优先级、幂等、失败隔离。 */
class AlertRuleEvaluationQueryHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T06:30:00Z");
    private static final long OWNER = 3L;
    private static final long OTHER_OWNER = 4L;

    private final InMemoryRules rules = new InMemoryRules();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final FakeFacts facts = new FakeFacts();
    private final FakeRecipients recipients = new FakeRecipients();
    private final RecordingMail mail = new RecordingMail();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void 非交易日不评估也不发送() {
        facts.tradingDay = false;
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        facts.funds(OWNER, fund(11L, "161725", true, "0.03", "0.10"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.evaluatedRules()).isZero();
        assertThat(mail.messages).isEmpty();
        assertThat(notifications.store).isEmpty();
    }

    @Test
    void 全局规则命中多只基金合并为一封邮件() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
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
    void 单基金规则只匹配自身基金() {
        rules.add(OWNER, AlertRuleScope.FUND, 12L, AlertRuleType.RISE, "0.01", true);
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
    void 盈利规则跳过非持仓基金() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.PROFIT, "0.05", true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", false, "0.03", "0.20"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(1);
        assertThat(mail.messages).singleElement().satisfies(message -> {
            assertThat(message.ruleType()).isEqualTo(AlertRuleType.PROFIT);
            assertThat(message.funds())
                    .extracting(AlertEmailGateway.AlertEmailMessage.FundRow::portfolioFundId)
                    .containsExactly(11L);
        });
    }

    @Test
    void 同一规则当日已发送后跳过() {
        AlertRule rule = rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        notifications.insert(AlertNotification.rehydrate(1L, null, OWNER, rule.id(), AlertRuleType.RISE,
                new BigDecimal("0.01"), BusinessDay.toDateLabel(NOW), AlertNotificationStatus.SENT,
                "owner@example.com", 1, "已发送", null, NOW));
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
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
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
        });

        recipients.emails.put(OWNER, "owner@example.com");
        assertThat(handler().evaluate().sentRules()).isEqualTo(1);
    }

    @Test
    void 单条规则异常不影响其它规则() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        rules.add(OTHER_OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
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
    void 单基金规则覆盖同类型全局规则且不误伤其它基金() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, AlertRuleType.RISE, "0.50", true);
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
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, AlertRuleType.RISE, "0.01", true);
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
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, AlertRuleType.RISE, "0.50", false);
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
    void 不同类型的单基金规则不覆盖全局规则() {
        rules.add(OWNER, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, "0.01", true);
        rules.add(OWNER, AlertRuleScope.FUND, 11L, AlertRuleType.PROFIT, "0.05", true);
        facts.funds(OWNER,
                fund(11L, "161725", true, "0.03", "0.10"),
                fund(12L, "000001", true, "0.02", "0.01"));
        recipients.emails.put(OWNER, "owner@example.com");

        var result = handler().evaluate();

        assertThat(result.sentRules()).isEqualTo(2);
        assertThat(mail.messages).extracting(AlertEmailGateway.AlertEmailMessage::ruleType)
                .containsExactlyInAnyOrder(AlertRuleType.RISE, AlertRuleType.PROFIT);
        assertThat(mail.messages).filteredOn(message -> message.ruleType() == AlertRuleType.RISE)
                .singleElement().satisfies(message -> assertThat(message.funds()).hasSize(2));
    }

    private AlertRuleEvaluationQueryHandler handler() {
        return new AlertRuleEvaluationQueryHandler(rules, notifications, facts, recipients,
                new AlertNotificationDispatchCommandHandler(notifications, mail, clock), clock);
    }

    private static AlertFundFactsGateway.AlertFundFact fund(long portfolioFundId, String fundCode,
                                                            boolean open, String dailyChangePct,
                                                            String holdingReturnRate) {
        return new AlertFundFactsGateway.AlertFundFact(portfolioFundId, fundCode, "基金" + fundCode,
                open ? "HOLDING" : "PENDING_HOLDING", open, new BigDecimal(dailyChangePct),
                new BigDecimal("10000"), new BigDecimal("500"), new BigDecimal(holdingReturnRate),
                new BigDecimal("1.2345"), NOW, "REALTIME");
    }

    private static final class InMemoryRules implements AlertRuleRepository {
        private final Map<Long, AlertRule> store = new LinkedHashMap<>();
        private long sequence;

        AlertRule add(long ownerId, AlertRuleScope scope, Long portfolioFundId, AlertRuleType type,
                      String threshold, boolean enabled) {
            AlertRule rule = AlertRule.rehydrate(++sequence, ownerId, scope, portfolioFundId, type,
                    new BigDecimal(threshold), enabled);
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
                    notification.threshold(),
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
