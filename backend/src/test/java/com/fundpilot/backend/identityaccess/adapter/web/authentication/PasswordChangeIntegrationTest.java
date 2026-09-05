package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class PasswordChangeIntegrationTest extends AbstractIntegrationTest {

    private static final String OLD_PASSWORD = "old-password-123";
    private static final String NEW_PASSWORD = "new-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAdministrationApi users;

    private String username;

    @BeforeEach
    void createUser() {
        username = "password-change-" + UUID.randomUUID();
        users.ensureBootstrapAdmin(username, OLD_PASSWORD);
    }

    @Test
    void changingPasswordExpiresOldCookieAndOldSession() throws Exception {
        String oldCookie = loginCookie(OLD_PASSWORD);

        mockMvc.perform(put("/api/auth/password")
                        .cookie(cookie(oldCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + OLD_PASSWORD
                                + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(get("/api/auth/verify").cookie(cookie(oldCookie)))
                .andExpect(status().isUnauthorized());

        String newCookie = loginCookie(NEW_PASSWORD);
        mockMvc.perform(get("/api/auth/verify").cookie(cookie(newCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"" + OLD_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void invalidCurrentPasswordAndPolicyViolationLeaveSessionAndPasswordUnchanged() throws Exception {
        String oldCookie = loginCookie(OLD_PASSWORD);

        mockMvc.perform(put("/api/auth/password")
                        .cookie(cookie(oldCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\""
                                + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        mockMvc.perform(put("/api/auth/password")
                        .cookie(cookie(oldCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + OLD_PASSWORD
                                + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));

        mockMvc.perform(get("/api/auth/verify").cookie(cookie(oldCookie)))
                .andExpect(status().isOk());
        loginCookie(OLD_PASSWORD);
    }

    private String loginCookie(String password) throws Exception {
        String header = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return cookieValue(header);
    }

    private Cookie cookie(String value) {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, value);
    }

    private String cookieValue(String header) {
        String prefix = AuthenticationFilter.COOKIE_NAME + "=";
        int start = header.indexOf(prefix) + prefix.length();
        int end = header.indexOf(';', start);
        return header.substring(start, end < 0 ? header.length() : end);
    }
}
