package com.fundpilot.backend.admin.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void correctKeyAllowsAdminRequest() throws Exception {
        MockHttpServletRequest request = adminRequest();
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, "test-admin-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingKeyRejectsAdminRequest() throws Exception {
        MockHttpServletRequest request = adminRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"ADMIN_UNAUTHORIZED\"");
        verifyNoInteractions(filterChain);
    }

    @Test
    void wrongKeyRejectsAdminRequest() throws Exception {
        MockHttpServletRequest request = adminRequest();
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
        MockHttpServletRequest request = adminRequest();
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
    void publicRequestDoesNotRequireKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/funds");
        request.setServletPath("/api/funds");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void encodedAdminPathStillRequiresKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/%61dmin/market-data/refresh");
        request.setRequestURI("/api/%61dmin/market-data/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void matrixParameterAdminPathStillRequiresKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/admin;x/market-data/refresh");
        request.setRequestURI("/api/admin;x/market-data/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    private MockHttpServletRequest adminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/market-data/refresh");
        request.setServletPath("/api/admin/market-data/refresh");
        return request;
    }
}
