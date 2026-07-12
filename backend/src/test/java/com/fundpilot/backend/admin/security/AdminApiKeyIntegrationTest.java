package com.fundpilot.backend.admin.security;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class AdminApiKeyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void missingKeyCannotReachAdminController() throws Exception {
        mockMvc.perform(post("/api/admin/transactions/confirm-nav"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void wrongKeyCannotReachAdminController() throws Exception {
        mockMvc.perform(post("/api/admin/transactions/confirm-nav")
                        .header(AdminApiKeyFilter.HEADER_NAME, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void encodedAdminPathCannotBypassFilter() throws Exception {
        mockMvc.perform(post(URI.create("/api/%61dmin/transactions/confirm-nav")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void matrixParameterAdminPathCannotBypassFilter() throws Exception {
        mockMvc.perform(post("/api/admin;x/transactions/confirm-nav"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void correctKeyReachesAdminController() throws Exception {
        mockMvc.perform(post("/api/admin/transactions/confirm-nav")
                        .header(AdminApiKeyFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void businessApiRequiresKey() throws Exception {
        mockMvc.perform(get("/api/funds"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(header().string(HttpHeaders.VARY, AdminApiKeyFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void correctKeyReachesBusinessApi() throws Exception {
        mockMvc.perform(get("/api/funds")
                        .header(AdminApiKeyFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void authVerificationRequiresKey() throws Exception {
        mockMvc.perform(get("/api/auth/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctKeyPassesAuthVerification() throws Exception {
        mockMvc.perform(get("/api/auth/verify")
                        .header(AdminApiKeyFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void actuatorDoesNotRequireKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
