package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import com.fundpilot.backend.admin.security.AdminSessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import com.fundpilot.backend.admin.security.AdminApiKeyFilter;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.user.entity.SiteUserEntity;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.repository.SiteUserRepository;
import com.fundpilot.backend.user.service.PasswordService;
import com.fundpilot.backend.admin.security.AdminApiKeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SiteAuthController {

    private final AdminSessionTokenService sessionTokenService;
    private final SiteUserRepository userRepository;
    private final PasswordService passwordService;
    private final AdminApiKeyProperties apiKeyProperties;

    @PostMapping("/login")
    public ApiResponse<AuthUserView> login(@RequestBody(required = false) LoginRequest login, HttpServletRequest request,
                                            HttpServletResponse response) {
        String legacyKey = request.getHeader(com.fundpilot.backend.admin.security.AdminApiKeyFilter.HEADER_NAME);
        if (login == null && legacyKey != null && apiKeyProperties.apiKey() != null
                && !apiKeyProperties.apiKey().isBlank()
                && MessageDigest.isEqual(legacyKey.getBytes(StandardCharsets.UTF_8),
                apiKeyProperties.apiKey().getBytes(StandardCharsets.UTF_8))) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    sessionCookie(request, sessionTokenService.issue()).build().toString());
            return ApiResponse.ok(new AuthUserView(0L, "admin", UserRole.ADMIN));
        }
        if (login == null || login.username() == null || login.username().isBlank() || login.password() == null) {
            throw ErrorCode.ADMIN_UNAUTHORIZED.toException("用户名或密码错误");
        }
        SiteUserEntity user = userRepository.findByUsername(login.username().trim()).orElseThrow(
                () -> ErrorCode.ADMIN_UNAUTHORIZED.toException("用户名或密码错误"));
        if (!user.isEnabled() || !passwordService.matches(login.password(), user.getPasswordHash())) {
            throw ErrorCode.ADMIN_UNAUTHORIZED.toException("用户名或密码错误");
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(request, sessionTokenService.issue(user.getId(), user.getRole())).build().toString());
        return ApiResponse.ok(AuthUserView.from(user));
    }

    @GetMapping("/verify")
    public ApiResponse<AuthUserView> verify(HttpServletRequest request) {
        AdminSessionTokenService.SessionIdentity identity = (AdminSessionTokenService.SessionIdentity)
                request.getAttribute(AdminApiKeyFilter.USER_ATTRIBUTE);
        if (identity.userId() == 0L) return ApiResponse.ok(new AuthUserView(0L, "admin", UserRole.ADMIN));
        SiteUserEntity user = userRepository.findById(identity.userId()).filter(SiteUserEntity::isEnabled)
                .orElseThrow(() -> ErrorCode.ADMIN_UNAUTHORIZED.toException("用户已停用"));
        return ApiResponse.ok(AuthUserView.from(user));
    }

    public record LoginRequest(String username, String password) {}
    public record AuthUserView(Long id, String username, UserRole role) {
        static AuthUserView from(SiteUserEntity user) {
            return new AuthUserView(user.getId(), user.getUsername(), user.getRole());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(request, "").maxAge(0).build().toString());
        return ApiResponse.ok(true);
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(HttpServletRequest request, String value) {
        boolean secure = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(AdminSessionTokenService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(AdminSessionTokenService.MAX_AGE);
    }
}
