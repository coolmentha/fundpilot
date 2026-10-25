package com.fundpilot.backend.identityaccess.adapter.web.opentoken;

import com.fundpilot.backend.identityaccess.adapter.api.opentoken.OpenApiTokenApi;
import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import com.fundpilot.backend.identityaccess.application.command.currentactor.CurrentActorCommandHandler;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

/** Open API 独立 Bearer 鉴权，不复用后台密码/会话或兼容管理密钥。 */
@Component
@RequiredArgsConstructor
public class OpenApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/api/open/v1/";
    public static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_ATTRIBUTE = "fundpilot.open-api-user";

    private final OpenApiTokenApi tokens;
    private final AuthenticationQueryHandler accounts;
    private final CurrentActorCommandHandler actors;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        // 认证错误统一 401，避免向调用方区分“Token 不存在”与“账号停用”。
        String credential = bearerToken(request.getHeader(AUTHORIZATION));
        Optional<AuthenticationQueryHandler.AuthenticatedActor> actor = credential == null
                ? Optional.empty() : tokens.findOwnerId(credential).flatMap(id -> activeUser(id, credential));
        if (actor.isEmpty()) {
            reject(response);
            return;
        }
        var identity = actor.orElseThrow();
        request.setAttribute(USER_ATTRIBUTE, new OpenApiRequestIdentity(identity.userId()));
        request.setAttribute(RequestActorAttributes.USER_ID, identity.userId());
        try (var ignored = actors.open(CurrentActor.user(identity.userId(), identity.role()))) {
            filterChain.doFilter(request, response);
        }
    }

    private Optional<AuthenticationQueryHandler.AuthenticatedActor> activeUser(long userId, String credential) {
        try {
            var actor = accounts.requireActive(userId);
            tokens.markUsed(credential, clock.instant());
            return Optional.of(actor);
        } catch (AuthenticationFailure ignored) {
            return Optional.empty();
        }
    }

    private static String bearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? null : value;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.OPEN_API_UNAUTHORIZED.name(), "Bearer Token 无效"));
    }

    public record OpenApiRequestIdentity(long userId) {
    }
}
