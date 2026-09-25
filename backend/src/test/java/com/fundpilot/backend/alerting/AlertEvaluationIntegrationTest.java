package com.fundpilot.backend.alerting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.query.notificationhistory.AlertNotificationHistoryQueryHandler;
import com.fundpilot.backend.alerting.application.query.ruleevaluation.AlertRuleEvaluationQueryHandler;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 端到端验证「每交易日 14:30 评估 → 发送 → 落库 → 历史可见」主链路：
 * 首次评估写一条 {@code SENT}，同规则同交易日的第二次评估被幂等跳过。
 *
 * <p>用固定 {@link Clock} 让交易日与净值日期可确定：{@code 2026-09-15T06:30:00Z} 即北京时间当日 14:30。
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = FundPilotBackendApplication.class)
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class AlertEvaluationIntegrationTest extends AbstractIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T06:30:00Z");

    @TestBean(methodName = "fixedClock")
    Clock clock;

    @MockitoBean MarketIndicatorRefreshCommandHandler marketRefresh;

    @MockitoBean AlertEmailGateway deliveries;

    @Autowired MockMvc mockMvc;
    @Autowired FundProductApi products;
    @Autowired PublishedNavRepository navs;
    @Autowired SessionTokenGateway sessions;
    @Autowired AlertRuleCommandHandler rules;
    @Autowired AlertRuleEvaluationQueryHandler evaluation;
    @Autowired AlertNotificationHistoryQueryHandler history;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private static Clock fixedClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    @Test
    void evaluationSendsOncePerTradingDayAndRecordsHistory() throws Exception {
        when(deliveries.send(any())).thenReturn(AlertEmailGateway.DeliveryResult.success());
        Instant tradingDate = BusinessDay.toDateLabel(FIXED_NOW);
        jdbc.update("""
                INSERT INTO trading_calendar (calendar_date, is_trading_day, version, created_date, updated_date)
                VALUES (?, true, 0, now(), now())
                ON CONFLICT (calendar_date) WHERE deleted_date IS NULL DO NOTHING
                """, Date.valueOf(tradingDate.atZone(ZoneOffset.UTC).toLocalDate()));

        long ownerId = testActorId();
        String recipient = "alert-" + UUID.randomUUID() + "@example.com";
        jdbc.update("UPDATE site_user SET email = ? WHERE id = ?", recipient, ownerId);

        long portfolioFundId = onboardOpenPosition();

        var rule = rules.create(ownerId, new AlertRuleCommandHandler.RuleInput(
                "FUND", portfolioFundId, null, "ALL",
                List.of(new AlertRuleCommandHandler.RuleInput.ConditionInput("DAILY_CHANGE", Map.of(),
                        "ABOVE", new BigDecimal("0.05"))),
                null, true));

        var first = evaluation.evaluate();
        assertThat(first.evaluatedRules()).isEqualTo(1);
        assertThat(first.matchedRules()).isEqualTo(1);
        assertThat(first.sentRules()).isEqualTo(1);
        assertThat(first.failedRules()).isZero();

        ArgumentCaptor<AlertEmailGateway.AlertEmailMessage> message =
                ArgumentCaptor.forClass(AlertEmailGateway.AlertEmailMessage.class);
        verify(deliveries).send(message.capture());
        assertThat(message.getValue().recipient()).isEqualTo(recipient);
        assertThat(message.getValue().conditionSummary()).isEqualTo("当日涨跌幅 高于 0.05");
        assertThat(message.getValue().fundCount()).isEqualTo(1);

        assertThat(sentCount(rule.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT recipient_email FROM alert_notification WHERE alert_rule_id = ? AND status = 'SENT'",
                String.class, rule.id())).isEqualTo(recipient);
        assertThat(jdbc.queryForObject(
                "SELECT trigger_summary FROM alert_notification WHERE alert_rule_id = ? AND status = 'SENT'",
                String.class, rule.id())).contains("命中").contains("当日涨跌幅");

        var second = evaluation.evaluate();
        assertThat(second.evaluatedRules()).isEqualTo(1);
        assertThat(second.matchedRules()).isZero();
        assertThat(second.sentRules()).isZero();
        assertThat(sentCount(rule.id())).isEqualTo(1L);

        var recorded = history.findLatest(ownerId, 20).stream()
                .filter(row -> row.alertRuleId() == rule.id())
                .findFirst().orElseThrow();
        assertThat(recorded.status()).isEqualTo("SENT");
        assertThat(recorded.ruleType()).isNull();
        assertThat(recorded.conditionsSnapshot()).contains("DAILY_CHANGE");
        assertThat(recorded.recipientEmail()).isEqualTo(recipient);
        assertThat(recorded.fundCount()).isEqualTo(1);
        assertThat(recorded.tradingDate()).isEqualTo(tradingDate);
        assertThat(recorded.failureReason()).isNull();
    }

    /** 造一只上涨 6% 的 OPEN 持仓基金：最新净值落在业务当日，前一日净值提供涨跌幅基准。 */
    private long onboardOpenPosition() throws Exception {
        var product = products.ensure(new FundProductApi.EnsureProduct(
                "P" + Long.toUnsignedString(System.nanoTime(), 36), "提醒集成测试 ETF", null,
                FundProductApi.InvestmentTarget.STOCK));
        navs.saveAll(List.of(PublishedNav.publish(null, product.id(), product.fundCode(),
                Instant.parse("2026-09-10T00:00:00Z"), new BigDecimal("3.00"), new BigDecimal("3.00"),
                Instant.parse("2026-09-10T08:00:00Z"))));
        String response = mockMvc.perform(post("/api/portfolio-funds").cookie(ownerCookie())
                        .contentType("application/json")
                        .content("{\"fundProductId\":" + product.id()
                                + ",\"initialHoldingShares\":1000,\"costPerShare\":3.00,"
                                + "\"openedAt\":\"2026-09-10T08:00:00Z\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        navs.saveAll(List.of(
                PublishedNav.publish(null, product.id(), product.fundCode(),
                        Instant.parse("2026-09-14T00:00:00Z"), new BigDecimal("1.00"), new BigDecimal("1.00"),
                        Instant.parse("2026-09-14T08:00:00Z")),
                PublishedNav.publish(null, product.id(), product.fundCode(),
                        BusinessDay.toDateLabel(FIXED_NOW), new BigDecimal("1.06"), new BigDecimal("1.06"),
                        Instant.parse("2026-09-15T07:00:00Z"))));
        return objectMapper.readTree(response).path("data").path("portfolioFundId").asLong();
    }

    private long sentCount(long ruleId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM alert_notification WHERE alert_rule_id = ? AND status = 'SENT'",
                Long.class, ruleId);
    }

    private Cookie ownerCookie() {
        return new Cookie(AuthenticationFilter.COOKIE_NAME,
                sessions.issue(testActorId(), UserRole.ADMIN, 0L));
    }
}
