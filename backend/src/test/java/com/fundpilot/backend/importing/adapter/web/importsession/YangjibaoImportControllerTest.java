package com.fundpilot.backend.importing.adapter.web.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportCommandHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = YangjibaoImportController.class)
@Import({YangjibaoImportController.class, YangjibaoImportControllerTest.TestConfig.class})
class YangjibaoImportControllerTest {
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean YangjibaoImportCommandHandler commands;

    @Test
    void listReturnsImportSessionSummaryContract() throws Exception {
        when(commands.listSessions()).thenReturn(List.of(
                new YangjibaoImportCommandHandler.ImportSessionSummary(
                        "session-1", "COMPLETED", Instant.parse("2026-08-30T08:00:00Z"),
                        Instant.parse("2026-08-30T08:03:00Z"), null, 2, 2, 1, 1)));

        mockMvc.perform(get("/api/imports/yangjibao/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-08-30T08:00:00Z"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-08-30T08:03:00Z"))
                .andExpect(jsonPath("$.data[0].expiresAt").hasJsonPath())
                .andExpect(jsonPath("$.data[0].total").value(2))
                .andExpect(jsonPath("$.data[0].processed").value(2))
                .andExpect(jsonPath("$.data[0].succeeded").value(1))
                .andExpect(jsonPath("$.data[0].failed").value(1));

        verify(commands).listSessions();
    }

    @Test
    void listMapsApplicationSummaryToWebView() {
        when(commands.listSessions()).thenReturn(List.of(
                new YangjibaoImportCommandHandler.ImportSessionSummary(
                        "session-1", "PROCESSING", Instant.parse("2026-08-30T08:00:00Z"),
                        Instant.parse("2026-08-30T08:03:00Z"), null, 2, 1, 1, 0)));

        var response = new YangjibaoImportController(commands).list();

        assertThat(response.data()).singleElement().satisfies(item -> {
            assertThat(item.getClass().getPackageName())
                    .isEqualTo("com.fundpilot.backend.importing.adapter.web.importsession");
            assertThat(item.getClass().getSimpleName()).endsWith("View");
        });
    }
}
