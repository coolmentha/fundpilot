package com.fundpilot.backend.alerting.adapter.web.rulemanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.alerting.adapter.web.notificationhistory.AlertNotificationController;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler;
import com.fundpilot.backend.alerting.application.query.indicatormetadata.IndicatorMetadataQueryHandler;
import com.fundpilot.backend.alerting.application.query.notificationhistory.AlertNotificationHistoryQueryHandler;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.alerting.application.query.ruleevaluation.AlertRuleEvaluationQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 提醒规则与提醒记录接口的 MVC 切片:校验路由、状态码与响应体结构。 */
@WebMvcTest(controllers = {AlertRuleController.class, AlertNotificationController.class})
@Import({AlertRuleController.class, AlertNotificationController.class,
        AlertRuleControllerTest.TestConfig.class})
class AlertRuleControllerTest {

    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AlertRuleCommandHandler commands;

    @MockitoBean
    AlertRuleQueryHandler queries;

    @MockitoBean
    AlertRuleEvaluationQueryHandler evaluation;

    @MockitoBean
    IndicatorMetadataQueryHandler indicators;

    @MockitoBean
    AlertNotificationHistoryQueryHandler historyQueries;

    @Test
    void list_returnsCurrentOwnersRules() throws Exception {
        when(queries.findByOwner(7L)).thenReturn(List.of(view()));

        mockMvc.perform(get("/api/alert-rules")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].scope").value("FUND"))
                .andExpect(jsonPath("$.data[0].portfolioFundId").value(41))
                .andExpect(jsonPath("$.data[0].fundCode").value("161725"))
                .andExpect(jsonPath("$.data[0].match").value("ALL"))
                .andExpect(jsonPath("$.data[0].kind").value("CONDITION"))
                .andExpect(jsonPath("$.data[0].kindLabel").value("条件提醒"))
                .andExpect(jsonPath("$.data[0].conditionSummary").value("当日涨跌幅 高于 0.05"))
                .andExpect(jsonPath("$.data[0].conditions[0].indicator").value("DAILY_CHANGE"))
                .andExpect(jsonPath("$.data[0].conditions[0].relation").value("ABOVE"))
                .andExpect(jsonPath("$.data[0].conditions[0].value").value(0.05))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].todaySent").value(false));
    }

    @Test
    void indicators_returnsMetadataForConditionBuilder() throws Exception {
        when(indicators.all()).thenReturn(List.of(metadata()));

        mockMvc.perform(get("/api/alert-rules/indicators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("PRICE_VS_MA"))
                .andExpect(jsonPath("$.data[0].label").value("净值与均线偏离率"))
                .andExpect(jsonPath("$.data[0].source").value("MARKET_DATA"))
                .andExpect(jsonPath("$.data[0].parameters[0].name").value("window"))
                .andExpect(jsonPath("$.data[0].parameters[0].label").value("均线天数"))
                .andExpect(jsonPath("$.data[0].parameters[0].defaultValue").value(250))
                .andExpect(jsonPath("$.data[0].parameters[0].minimum").value(5))
                .andExpect(jsonPath("$.data[0].relations[0].relation").value("CROSS_BELOW"))
                .andExpect(jsonPath("$.data[0].relations[0].label").value("下穿"))
                .andExpect(jsonPath("$.data[0].relations[0].thresholded").value(true))
                .andExpect(jsonPath("$.data[0].relations[0].defaultThreshold").value(0));
    }

    @Test
    void preview_returnsConditionValues() throws Exception {
        when(evaluation.preview(eq(7L), any())).thenReturn(previewResult());

        mockMvc.perform(post("/api/alert-rules/preview")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"FUND","portfolioFundId":41,
                                 "conditions":[{"indicator":"DAILY_CHANGE","relation":"ABOVE","value":0.05}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hit").value(true))
                .andExpect(jsonPath("$.data.tradingDate").exists())
                .andExpect(jsonPath("$.data.funds[0].portfolioFundId").value(41))
                .andExpect(jsonPath("$.data.funds[0].fundCode").value("161725"))
                .andExpect(jsonPath("$.data.funds[0].conditions[0].text").value("当日涨跌幅 高于 0.05"))
                .andExpect(jsonPath("$.data.funds[0].conditions[0].currentValue").value(0.0732))
                .andExpect(jsonPath("$.data.funds[0].conditions[0].satisfied").value(true));
    }

    @Test
    void create_returnsCreatedRule() throws Exception {
        when(commands.create(eq(7L), any())).thenReturn(view());

        mockMvc.perform(post("/api/alert-rules")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"FUND","portfolioFundId":41,"match":"ALL",
                                 "conditions":[{"indicator":"DAILY_CHANGE","relation":"ABOVE","value":0.05}],
                                 "enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.conditionSummary").value("当日涨跌幅 高于 0.05"));
    }

    @Test
    void createTrailingStop_returnsParameters() throws Exception {
        when(commands.create(eq(7L), any())).thenReturn(trailingStopView());

        mockMvc.perform(post("/api/alert-rules")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"GLOBAL","kind":"TRAILING_STOP",
                                 "takeProfit":{"activation":0.15,"pullback":0.06,"harvest":0.5,
                                               "minimumHolding":0.5,"maxSingleSell":0.2,"cooldownDays":10},
                                 "enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kind").value("TRAILING_STOP"))
                .andExpect(jsonPath("$.data.kindLabel").value("回撤止盈"))
                .andExpect(jsonPath("$.data.match").isEmpty())
                .andExpect(jsonPath("$.data.conditions").isEmpty())
                .andExpect(jsonPath("$.data.takeProfit.activation").value(0.15))
                .andExpect(jsonPath("$.data.takeProfit.cooldownDays").value(10));
    }

    @Test
    void update_returnsUpdatedRule() throws Exception {
        when(commands.update(eq(7L), eq(5L), any())).thenReturn(view());

        mockMvc.perform(put("/api/alert-rules/5")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"GLOBAL","conditions":[{"indicator":"INDEX_PE_PERCENTILE",
                                 "relation":"BELOW","value":0.3}],"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    void enable_returnsToggledRule() throws Exception {
        when(commands.changeEnabled(7L, 5L, true)).thenReturn(view());

        mockMvc.perform(post("/api/alert-rules/5/enable")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void disable_returnsToggledRule() throws Exception {
        when(commands.changeEnabled(7L, 5L, false)).thenReturn(view());

        mockMvc.perform(post("/api/alert-rules/5/disable")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_returnsSuccessWithoutData() throws Exception {
        mockMvc.perform(delete("/api/alert-rules/5")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void history_returnsLatestNotifications() throws Exception {
        when(historyQueries.findLatest(7L, 20)).thenReturn(List.of(notification()));

        mockMvc.perform(get("/api/alert-notifications")
                        .param("limit", "20")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(9))
                .andExpect(jsonPath("$.data[0].alertRuleId").value(5))
                .andExpect(jsonPath("$.data[0].ruleType").value("RISE"))
                .andExpect(jsonPath("$.data[0].conditionsSnapshot").value("{\"match\":\"ALL\"}"))
                .andExpect(jsonPath("$.data[0].fundCount").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SENT"))
                .andExpect(jsonPath("$.data[0].recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.data[0].sentAt").exists());
    }

    private static AlertRuleQueryHandler.RuleViewResult view() {
        return new AlertRuleQueryHandler.RuleViewResult(5L, "FUND", 41L, "161725", "招商中证白酒",
                "CONDITION", "条件提醒", "ALL", "当日涨跌幅 高于 0.05",
                List.of(new AlertRuleQueryHandler.ConditionView("DAILY_CHANGE", Map.of(), "ABOVE",
                        new BigDecimal("0.05"))),
                null, true, false, null);
    }

    private static AlertRuleQueryHandler.RuleViewResult trailingStopView() {
        return new AlertRuleQueryHandler.RuleViewResult(6L, "GLOBAL", null, null, null,
                "TRAILING_STOP", "回撤止盈", null,
                "回撤止盈：盈利达 15% 后回撤 6% 即提醒",
                List.of(),
                new AlertRuleQueryHandler.TakeProfitView(new BigDecimal("0.15"), new BigDecimal("0.06"),
                        new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.2"), 10),
                true, false, null);
    }

    private static IndicatorMetadataQueryHandler.IndicatorMetadata metadata() {
        return new IndicatorMetadataQueryHandler.IndicatorMetadata("PRICE_VS_MA", "净值与均线偏离率",
                "净值相对均线的偏离百分比，负值表示跌破均线", "MARKET_DATA",
                new BigDecimal("-1"), new BigDecimal("1"),
                List.of(new IndicatorMetadataQueryHandler.ParameterMetadata("window", "均线天数", 250, 5, 250)),
                List.of(new IndicatorMetadataQueryHandler.RelationMetadata("CROSS_BELOW", "下穿", true,
                        BigDecimal.ZERO)));
    }

    private static AlertRuleEvaluationQueryHandler.PreviewResult previewResult() {
        return new AlertRuleEvaluationQueryHandler.PreviewResult(Instant.parse("2026-09-25T00:00:00Z"), true,
                List.of(new AlertRuleEvaluationQueryHandler.FundPreview(41L, "161725", "招商中证白酒", true,
                        List.of(new AlertRuleEvaluationQueryHandler.ConditionPreview("当日涨跌幅 高于 0.05",
                                new BigDecimal("0.0732"), true)))));
    }

    private static AlertNotificationHistoryQueryHandler.NotificationViewResult notification() {
        return new AlertNotificationHistoryQueryHandler.NotificationViewResult(9L, 5L, "RISE",
                new BigDecimal("0.05"), "{\"match\":\"ALL\"}",
                "招商中证白酒(161725) 上涨6.00%", 1, "SENT", null,
                Instant.parse("2026-09-21T06:30:00Z"), Instant.parse("2026-09-21T06:30:01Z"),
                "user@example.com");
    }
}
