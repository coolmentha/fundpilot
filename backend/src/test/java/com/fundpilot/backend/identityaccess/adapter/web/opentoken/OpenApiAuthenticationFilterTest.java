package com.fundpilot.backend.identityaccess.adapter.web.opentoken;

import com.fundpilot.backend.identityaccess.adapter.api.opentoken.OpenApiTokenApi;
import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import com.fundpilot.backend.identityaccess.application.command.currentactor.CurrentActorCommandHandler;
import com.fundpilot.backend.identityaccess.application.gateway.currentactor.ActorContext;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler.AuthenticatedActor;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenApiAuthenticationFilterTest {

    private final FilterChain chain = mock(FilterChain.class);
    private final OpenApiTokenApi tokens = mock(OpenApiTokenApi.class);
    private final AuthenticationQueryHandler accounts = mock(AuthenticationQueryHandler.class);
    private final CurrentActorCommandHandler actors = mock(CurrentActorCommandHandler.class);
    private OpenApiAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        when(actors.open(any())).thenReturn(mock(ActorContext.Scope.class));
        filter = new OpenApiAuthenticationFilter(tokens, accounts, actors,
                JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void validUserTokenBindsOwnerAndRecordsUse() throws Exception {
        when(tokens.findOwnerId("fp_valid")).thenReturn(Optional.of(7L));
        when(accounts.requireActive(7L)).thenReturn(new AuthenticatedActor(7L, "alice", ActorRole.USER, null));
        var request = request("/api/open/v1/market/status", "Bearer fp_valid");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokens).markUsed("fp_valid", Instant.parse("2026-10-25T00:00:00Z"));
        assertThat(request.getAttribute(OpenApiAuthenticationFilter.USER_ATTRIBUTE))
                .isEqualTo(new OpenApiAuthenticationFilter.OpenApiRequestIdentity(7L));
    }

    @Test
    void adminOwnerStillReadsOnlyOwnDataThroughOpenApi() throws Exception {
        when(tokens.findOwnerId("fp_admin")).thenReturn(Optional.of(1L));
        when(accounts.requireActive(1L)).thenReturn(new AuthenticatedActor(1L, "admin", ActorRole.ADMIN, null));
        var request = request("/api/open/v1/market/status", "Bearer fp_admin");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getAttribute(OpenApiAuthenticationFilter.USER_ATTRIBUTE))
                .isEqualTo(new OpenApiAuthenticationFilter.OpenApiRequestIdentity(1L));
    }

    @Test
    void disabledUserTokenIsRejected() throws Exception {
        when(tokens.findOwnerId("fp_disabled")).thenReturn(Optional.of(8L));
        when(accounts.requireActive(8L)).thenThrow(new AuthenticationFailure(
                AuthenticationFailure.Code.ADMIN_UNAUTHORIZED, "用户已停用"));
        var request = request("/api/open/v1/market/status", "Bearer fp_disabled");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("OPEN_API_UNAUTHORIZED");
        verifyNoInteractions(chain);
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        var request = request("/api/open/v1/market/status", null);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void nonOpenApiRequestIsIgnored() throws Exception {
        var request = request("/api/market/status", "Bearer fp_valid");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(tokens, accounts);
    }

    private static MockHttpServletRequest request(String path, String authorization) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }
}
