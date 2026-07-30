package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.command.currentactor.CurrentActorCommandHandler;
import com.fundpilot.backend.identityaccess.application.gateway.currentactor.ActorContext;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler.AuthenticatedActor;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private final FilterChain chain = mock(FilterChain.class);
    private final AuthenticationQueryHandler authentication = mock(AuthenticationQueryHandler.class);
    private final CurrentActorCommandHandler actorContext = mock(CurrentActorCommandHandler.class);
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        when(actorContext.open(any())).thenReturn(mock(ActorContext.Scope.class));
        filter = new AuthenticationFilter(JsonMapper.builder().build(), authentication, actorContext);
    }

    @Test
    void adminOrdinaryRequestBindsItsRealIdentity() throws Exception {
        when(authentication.authenticate("key", null))
                .thenReturn(Optional.of(new AuthenticatedActor(7L, "admin", ActorRole.ADMIN)));
        MockHttpServletRequest request = apiRequest("/api/funds");
        request.addHeader(AuthenticationFilter.HEADER_NAME, "key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        RequestIdentity identity = (RequestIdentity) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        assertThat(identity.userId()).isEqualTo(7L);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store, private");
    }

    @Test
    void regularUserCannotAccessAdminEntry() throws Exception {
        when(authentication.authenticate(isNull(), isNull()))
                .thenReturn(Optional.of(new AuthenticatedActor(8L, "user", ActorRole.USER)));
        MockHttpServletRequest request = apiRequest("/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ADMIN_FORBIDDEN");
        verifyNoInteractions(chain);
    }

    @Test
    void missingCredentialIsRejected() throws Exception {
        when(authentication.authenticate(isNull(), isNull())).thenReturn(Optional.empty());
        MockHttpServletRequest request = apiRequest("/api/funds");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void nonApiRequestIsNotFiltered() throws Exception {
        MockHttpServletRequest request = apiRequest("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(authentication);
    }

    private MockHttpServletRequest apiRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
