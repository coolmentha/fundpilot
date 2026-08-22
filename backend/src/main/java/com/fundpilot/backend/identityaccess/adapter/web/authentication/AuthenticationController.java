package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationCommandHandler;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证接口", description = "认证相关操作")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationCommandHandler commands;
    private final AuthenticationQueryHandler queries;
    private final SessionTokenGateway sessions;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public IdentityApiResponse<AuthUserView> login(@RequestBody(required = false) LoginRequest login,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        var result = commands.login(login == null ? null : login.username(),
                login == null ? null : login.password(), request.getHeader(AuthenticationFilter.HEADER_NAME));
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(request, result.sessionToken()).build().toString());
        return IdentityApiResponse.ok(new AuthUserView(result.userId(), result.username(), result.role().name()));
    }

    @GetMapping("/verify")
    @Operation(summary = "校验登录状态")
    public IdentityApiResponse<AuthUserView> verify(HttpServletRequest request) {
        RequestIdentity identity = (RequestIdentity) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        var actor = queries.requireActive(identity.userId());
        return IdentityApiResponse.ok(new AuthUserView(actor.userId(), actor.username(), actor.role().name()));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出登录")
    public IdentityApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(request, "").maxAge(0).build().toString());
        return IdentityApiResponse.ok(true);
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(HttpServletRequest request, String value) {
        boolean secure = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(AuthenticationFilter.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(sessions.maxAge());
    }

    @Schema(description = "登录请求")
    public record LoginRequest(
            @Schema(description = "用户名", example = "zhangsan") String username,
            @Schema(description = "密码", example = "P@ssw0rd123") String password) {
    }

    @Schema(description = "当前登录用户视图")
    public record AuthUserView(
            @Schema(description = "用户ID", example = "1") long id,
            @Schema(description = "用户名", example = "zhangsan") String username,
            @Schema(description = "用户角色", example = "ADMIN") String role) {
    }
}
