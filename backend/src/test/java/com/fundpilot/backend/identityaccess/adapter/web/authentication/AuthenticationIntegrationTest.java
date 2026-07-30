package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.UserResult;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired SessionTokenGateway sessions;
    @Autowired UserAdministrationApi users;

    private UserResult admin;

    @BeforeEach
    void setUpAdmin() {
        admin = users.ensureBootstrapAdmin("integration-admin", "test-password");
    }

    @Test
    void apiRequiresAuthenticationAndCannotBeBypassedByEncodedPath() throws Exception {
        mockMvc.perform(get("/api/funds"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(header().string(HttpHeaders.VARY, AuthenticationFilter.HEADER_NAME));
        mockMvc.perform(post(URI.create("/api/%61dmin/transactions/confirm-nav")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyKeyUsesRealAdminIdentityForOrdinaryAndAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/funds").header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/verify")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testActorId()));
    }

    @Test
    void loginIssuesPersistentCookieAndCookieRestoresSession() throws Exception {
        String setCookie = mockMvc.perform(post("/api/auth/login")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Secure")))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        mockMvc.perform(get("/api/auth/verify")
                        .cookie(new Cookie(AuthenticationFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testActorId()));
    }

    @Test
    void signedSessionReachesBusinessApiAndActuatorRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/funds").cookie(new Cookie(AuthenticationFilter.COOKIE_NAME,
                        sessions.issue(admin.id(), UserRole.ADMIN))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
