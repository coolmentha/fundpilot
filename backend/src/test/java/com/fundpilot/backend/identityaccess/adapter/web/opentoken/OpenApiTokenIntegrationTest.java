package com.fundpilot.backend.identityaccess.adapter.web.opentoken;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class OpenApiTokenIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserAdministrationApi users;
    @Autowired SessionTokenGateway sessions;

    @Test
    void tokenCanOnlyAccessOpenApiNamespaceAndRefreshInvalidatesOldToken() throws Exception {
        Cookie cookie = session(user("api-user").id());

        String first = issue(cookie);
        mockMvc.perform(get("/api/open/v1/market/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/market/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/market/status").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/open/v1/market/status").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/open/v1/market/status").cookie(cookie))
                .andExpect(status().isUnauthorized());

        String second = issue(cookie);
        mockMvc.perform(get("/api/open/v1/market/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/open/v1/market/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void revokeRejectsPreviouslyIssuedToken() throws Exception {
        Cookie cookie = session(user("revoked-user").id());
        String token = issue(cookie);

        mockMvc.perform(delete("/api/open-api/token").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/open/v1/market/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusDoesNotExposePlaintextToken() throws Exception {
        Cookie cookie = session(user("status-user").id());
        issue(cookie);

        mockMvc.perform(get("/api/open-api/token").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.nullValue()));
    }

    private UserAdministrationApi.UserResult user(String username) {
        return users.create(new CurrentActorApi.Actor(testActorId(), CurrentActorApi.ActorRole.ADMIN, false),
                new UserAdministrationApi.CreateUserRequest(username, "Strong-password1!", UserAdministrationApi.Role.USER));
    }

    private Cookie session(long userId) {
        return new Cookie("fundpilot_session", sessions.issue(userId, UserRole.USER, 0L));
    }

    private String issue(Cookie cookie) throws Exception {
        String body = mockMvc.perform(post("/api/open-api/token").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn().getResponse().getContentAsString();
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(body)
                .path("data").path("token").asText();
    }
}
