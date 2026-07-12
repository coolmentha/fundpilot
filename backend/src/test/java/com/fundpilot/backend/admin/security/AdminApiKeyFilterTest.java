package com.fundpilot.backend.admin.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private AdminApiKeyFilter createFilter(String configuredKey) {
        return new AdminApiKeyFilter(
                JsonMapper.builder().build(),
                new AdminApiKeyProperties(configuredKey)
        );
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
