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

/**
 * 使用部署环境注入的单一 API Key 保护所有业务 API。
 * 配置为空时保持失败关闭,避免部署漏配后退化为匿名可调用。
 */
@Component
@RequiredArgsConstructor
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Admin-Key";

    private final JsonMapper jsonMapper;
    private final AdminApiKeyProperties properties;
    private final AdminSessionTokenService sessionTokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        response.addHeader(HttpHeaders.VARY, HEADER_NAME);
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ErrorCode.ADMIN_AUTH_NOT_CONFIGURED, "访问鉴权未配置");
            return;
        }

        String suppliedKey = request.getHeader(HEADER_NAME);
        if (!matches(suppliedKey) && !hasValidSession(request)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.ADMIN_UNAUTHORIZED, "访问凭据无效");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String suppliedKey) {
        if (suppliedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                properties.apiKey().getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean hasValidSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (AdminSessionTokenService.COOKIE_NAME.equals(cookie.getName())) {
                return sessionTokenService.isValid(cookie.getValue());
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response, int status, ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(code.name(), message));
    }
}
