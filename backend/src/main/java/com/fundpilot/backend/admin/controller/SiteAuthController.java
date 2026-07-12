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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SiteAuthController {

    private final AdminSessionTokenService sessionTokenService;

    @PostMapping("/login")
    public ApiResponse<Boolean> login(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(request, sessionTokenService.issue()).build().toString());
        return ApiResponse.ok(true);
    }

    @GetMapping("/verify")
    public ApiResponse<Boolean> verify() {
        return ApiResponse.ok(true);
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
