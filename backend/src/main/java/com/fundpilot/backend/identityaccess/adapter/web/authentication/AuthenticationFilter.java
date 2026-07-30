package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import com.fundpilot.backend.identityaccess.application.command.currentactor.CurrentActorCommandHandler;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Admin-Key";
    public static final String COOKIE_NAME = "fundpilot_session";
    public static final String USER_ATTRIBUTE = "fundpilot.authenticated-user";

    private final JsonMapper jsonMapper;
    private final AuthenticationQueryHandler authentication;
    private final CurrentActorCommandHandler actorContext;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (path.equals("/api/auth/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        response.addHeader(HttpHeaders.VARY, HEADER_NAME);
        String suppliedKey = request.getHeader(HEADER_NAME);
        Optional<AuthenticationQueryHandler.AuthenticatedActor> identity = authentication.authenticate(
                suppliedKey, sessionToken(request));
        if (identity.isEmpty()) {
            if (suppliedKey != null && !authentication.legacyKeyConfigured()) {
                reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        AuthenticationFailure.Code.ADMIN_AUTH_NOT_CONFIGURED, "兼容管理密钥未配置");
            } else {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                        AuthenticationFailure.Code.ADMIN_UNAUTHORIZED, "访问凭据无效");
            }
            return;
        }
        var actor = identity.orElseThrow();
        if (path.startsWith("/api/admin/") && actor.role() != ActorRole.ADMIN) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    AuthenticationFailure.Code.ADMIN_FORBIDDEN, "需要管理员权限");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, new RequestIdentity(actor.userId(), actor.role()));
        request.setAttribute(RequestActorAttributes.USER_ID, actor.userId());
        try (var ignored = actorContext.open(CurrentActor.user(actor.userId(), actor.role()))) {
            filterChain.doFilter(request, response);
        }
    }

    private String sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void reject(HttpServletResponse response, int status, AuthenticationFailure.Code code,
                        String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), IdentityApiResponse.error(code.name(), message));
    }
}
