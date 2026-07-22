package com.fundpilot.backend.admin.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class AdminApiKeyFilterTest {

    private final FilterChain filterChain = mock(FilterChain.class);
    private AdminApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = createFilter("test-admin-key");
    }

    @Test
    void correctKeyAllowsApiRequest() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, "test-admin-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store, private");
        assertThat(response.getHeader(HttpHeaders.VARY)).isEqualTo(AdminApiKeyFilter.HEADER_NAME);
    }

    @Test
    void missingKeyRejectsApiRequest() throws Exception {
        MockHttpServletRequest request = apiRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"ADMIN_UNAUTHORIZED\"");
        verifyNoInteractions(filterChain);
    }

    @Test
    void wrongKeyRejectsApiRequest() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"ADMIN_UNAUTHORIZED\"");
        verifyNoInteractions(filterChain);
    }

    @Test
    void missingConfigurationFailsClosed() throws Exception {
        filter = createFilter("");
        MockHttpServletRequest request = apiRequest();
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, "any-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"ADMIN_AUTH_NOT_CONFIGURED\"");
        verifyNoInteractions(filterChain);
    }

    @Test
    void validSessionCookieAllowsApiRequestWithoutRawKey() throws Exception {
        AdminSessionTokenService sessions = createSessionService("test-admin-key");
        filter = createFilter("test-admin-key", sessions);
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie(AdminSessionTokenService.COOKIE_NAME, sessions.issue()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validUserSessionDoesNotRequireLegacyApiKey() throws Exception {
        AdminApiKeyProperties properties = new AdminApiKeyProperties("", "session-secret");
        AdminSessionTokenService sessions = new AdminSessionTokenService(properties,
                Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC));
        var users = mock(com.fundpilot.backend.user.repository.SiteUserRepository.class);
        var user = new com.fundpilot.backend.user.entity.SiteUserEntity();
        user.setId(7L);
        user.setRole(com.fundpilot.backend.user.entity.UserRole.USER);
        user.setEnabled(true);
        org.mockito.Mockito.when(users.findById(7L)).thenReturn(java.util.Optional.of(user));
        filter = new AdminApiKeyFilter(JsonMapper.builder().build(), properties, sessions, users);
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie(AdminSessionTokenService.COOKIE_NAME,
                sessions.issue(7L, com.fundpilot.backend.user.entity.UserRole.USER)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void regularUserCannotAccessAdminApi() throws Exception {
        AdminSessionTokenService sessions = createSessionService("test-admin-key");
        var users = mock(com.fundpilot.backend.user.repository.SiteUserRepository.class);
        var user = new com.fundpilot.backend.user.entity.SiteUserEntity();
        user.setId(7L);
        user.setRole(com.fundpilot.backend.user.entity.UserRole.USER);
        user.setEnabled(true);
        org.mockito.Mockito.when(users.findById(7L)).thenReturn(java.util.Optional.of(user));
        filter = new AdminApiKeyFilter(JsonMapper.builder().build(),
                new AdminApiKeyProperties("test-admin-key", ""), sessions, users);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.setServletPath("/api/admin/users");
        request.setCookies(new Cookie(AdminSessionTokenService.COOKIE_NAME,
                sessions.issue(7L, com.fundpilot.backend.user.entity.UserRole.USER)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"ADMIN_FORBIDDEN\"");
        verifyNoInteractions(filterChain);
    }

    @Test
    void invalidSessionCookieIsRejected() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie(AdminSessionTokenService.COOKIE_NAME, "invalid"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    private AdminApiKeyFilter createFilter(String configuredKey) {
        return createFilter(configuredKey, createSessionService(configuredKey));
    }

    private AdminApiKeyFilter createFilter(String configuredKey, AdminSessionTokenService sessions) {
        return new AdminApiKeyFilter(
                JsonMapper.builder().build(),
                new AdminApiKeyProperties(configuredKey, ""),
                sessions,
                mock(com.fundpilot.backend.user.repository.SiteUserRepository.class)
        );
    }

    private AdminSessionTokenService createSessionService(String configuredKey) {
        return new AdminSessionTokenService(
                new AdminApiKeyProperties(configuredKey, ""),
                Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void nonApiRequestDoesNotRequireKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setServletPath("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void encodedApiPathStillRequiresKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/%61pi/funds");
        request.setRequestURI("/%61pi/funds");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void matrixParameterApiPathStillRequiresKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api;x/funds");
        request.setRequestURI("/api;x/funds");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    private MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/funds");
        request.setServletPath("/api/funds");
        return request;
    }
}
