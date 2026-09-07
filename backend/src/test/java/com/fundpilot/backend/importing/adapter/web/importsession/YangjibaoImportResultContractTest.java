package com.fundpilot.backend.importing.adapter.web.importsession;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportCommandHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class YangjibaoImportResultContractTest {
    @Test
    void importResultIncludesFailureFieldsAndExplicitSuccessNulls() throws Exception {
        var commands = mock(YangjibaoImportCommandHandler.class);
        when(commands.importStatus("session-003")).thenReturn(new YangjibaoImportCommandHandler.ImportJobView(
                YangjibaoImportCommandHandler.ImportStatus.COMPLETED, 2, 2, 1, 1, null, List.of(
                new YangjibaoImportCommandHandler.ImportResult("a:h1", "017093", "FAILED",
                        "IMPORT_DEPENDENCY_FAILED", "暂时无法完成导入，请稍后重试", "imp-003"),
                new YangjibaoImportCommandHandler.ImportResult("a:h2", "017094", "CREATED",
                        null, "已新增基金", null))));

        standaloneSetup(new YangjibaoImportController(commands)).build()
                .perform(get("/api/imports/yangjibao/sessions/session-003/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].failureCode").value("IMPORT_DEPENDENCY_FAILED"))
                .andExpect(jsonPath("$.data.results[0].correlationId").value("imp-003"))
                .andExpect(jsonPath("$.data.results[1].failureCode").value(nullValue()))
                .andExpect(jsonPath("$.data.results[1].correlationId").value(nullValue()));
    }
}
