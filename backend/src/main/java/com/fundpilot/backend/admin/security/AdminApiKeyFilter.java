package com.fundpilot.backend.admin.security;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.repository.SiteUserRepository;

/**
 * 使用部署环境注入的单一 API Key 保护所有业务 API。
 * 配置为空时保持失败关闭,避免部署漏配后退化为匿名可调用。
 */
@Component
@RequiredArgsConstructor
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Admin-Key";
    public static final String USER_ATTRIBUTE = "fundpilot.authenticated-user";

    private final JsonMapper jsonMapper;
    private final AdminApiKeyProperties properties;
    private final AdminSessionTokenService sessionTokenService;
    private final SiteUserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (path.equals("/api/auth/login") && "POST".equalsIgnoreCase(request.getMethod())) return true;
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        response.addHeader(HttpHeaders.VARY, HEADER_NAME);
        String suppliedKey = request.getHeader(HEADER_NAME);
        Optional<AdminSessionTokenService.SessionIdentity> identity = sessionIdentity(request).flatMap(this::activeIdentity);
        boolean legacyAdmin = matches(suppliedKey);
        if (!legacyAdmin && identity.isEmpty()) {
            if (suppliedKey != null && (properties.apiKey() == null || properties.apiKey().isBlank())) {
                reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        ErrorCode.ADMIN_AUTH_NOT_CONFIGURED, "兼容管理密钥未配置");
                return;
            }
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.ADMIN_UNAUTHORIZED, "访问凭据无效");
            return;
        }

        if (path.startsWith("/api/admin/") && !legacyAdmin
                && identity.map(i -> i.role() != UserRole.ADMIN).orElse(true)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ADMIN_FORBIDDEN, "需要管理员权限");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, legacyAdmin
                ? new AdminSessionTokenService.SessionIdentity(0L, UserRole.ADMIN)
                : identity.orElseThrow());

        filterChain.doFilter(request, response);
    }

    private Optional<AdminSessionTokenService.SessionIdentity> activeIdentity(
            AdminSessionTokenService.SessionIdentity identity) {
        if (identity.userId() == 0L) return Optional.of(identity);
        return userRepository.findById(identity.userId())
                .filter(user -> user.isEnabled())
                .map(user -> new AdminSessionTokenService.SessionIdentity(user.getId(), user.getRole()));
    }

    private boolean matches(String suppliedKey) {
        if (suppliedKey == null || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                properties.apiKey().getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Optional<AdminSessionTokenService.SessionIdentity> sessionIdentity(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (AdminSessionTokenService.COOKIE_NAME.equals(cookie.getName())) {
                return sessionTokenService.parse(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void reject(HttpServletResponse response, int status, ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(code.name(), message));
    }
}
