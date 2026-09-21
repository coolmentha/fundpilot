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
import com.fundpilot.backend.alerting.application.query.notificationhistory.AlertNotificationHistoryQueryHandler;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
                .andExpect(jsonPath("$.data[0].ruleType").value("RISE"))
                .andExpect(jsonPath("$.data[0].threshold").value(0.05))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].todaySent").value(false));
    }

    @Test
    void create_returnsCreatedRule() throws Exception {
        when(commands.create(eq(7L), any())).thenReturn(view());

        mockMvc.perform(post("/api/alert-rules")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"FUND","portfolioFundId":41,"ruleType":"RISE",
                                 "threshold":0.05,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.ruleType").value("RISE"));
    }

    @Test
    void update_returnsUpdatedRule() throws Exception {
        when(commands.update(eq(7L), eq(5L), any())).thenReturn(view());

        mockMvc.perform(put("/api/alert-rules/5")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"GLOBAL","ruleType":"DROP","threshold":0.03,"enabled":false}
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
                .andExpect(jsonPath("$.data[0].fundCount").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SENT"))
                .andExpect(jsonPath("$.data[0].recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.data[0].sentAt").exists());
    }

    private static AlertRuleQueryHandler.RuleViewResult view() {
        return new AlertRuleQueryHandler.RuleViewResult(5L, "FUND", 41L, "161725", "招商中证白酒",
                "RISE", new BigDecimal("0.05"), true, false, null);
    }

    private static AlertNotificationHistoryQueryHandler.NotificationViewResult notification() {
        return new AlertNotificationHistoryQueryHandler.NotificationViewResult(9L, 5L, "RISE",
                new BigDecimal("0.05"), "招商中证白酒(161725) 上涨5.32%", 1, "SENT", null,
                Instant.parse("2026-09-21T06:30:00Z"), Instant.parse("2026-09-21T06:30:01Z"),
                "user@example.com");
    }
}
