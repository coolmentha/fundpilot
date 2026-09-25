package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationCommandHandler;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.LoginClientAddressResolver;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.application.query.authentication.AuthenticationQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    private final LoginClientAddressResolver clientAddresses;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ApiResponse<AuthUserView> login(@RequestBody(required = false) LoginRequest login,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        var result = commands.login(login == null ? null : login.username(),
                login == null ? null : login.password(), request.getHeader(AuthenticationFilter.HEADER_NAME),
                clientAddresses.resolve(request.getRemoteAddr(),
                        request.getHeader(LoginClientAddressResolver.FORWARDED_FOR_HEADER)));
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(request, result.sessionToken()).build().toString());
        return ApiResponse.ok(new AuthUserView(result.userId(), result.username(), result.role().name(),
                result.email()));
    }

    @GetMapping("/verify")
    @Operation(summary = "校验登录状态")
    public ApiResponse<AuthUserView> verify(HttpServletRequest request) {
        RequestIdentity identity = (RequestIdentity) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        var actor = queries.requireActive(identity.userId());
        return ApiResponse.ok(new AuthUserView(actor.userId(), actor.username(), actor.role().name(),
                actor.email()));
    }

    @PutMapping("/email")
    @Operation(summary = "修改当前用户提醒邮箱")
    public ApiResponse<AuthUserView> changeEmail(@RequestBody EmailChangeRequest emailChange,
                                                        HttpServletRequest request) {
        RequestIdentity identity = (RequestIdentity) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        String email = commands.changeEmail(identity.userId(), emailChange == null ? null : emailChange.email());
        var actor = queries.requireActive(identity.userId());
        return ApiResponse.ok(new AuthUserView(actor.userId(), actor.username(), actor.role().name(), email));
    }

    @PutMapping("/password")
    @Operation(summary = "修改当前用户密码")
    public ApiResponse<Boolean> changePassword(@RequestBody PasswordChangeRequest passwordChange,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        RequestIdentity identity = (RequestIdentity) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        boolean changed = commands.changePassword(identity.userId(), passwordChange.currentPassword(),
                passwordChange.newPassword());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(request, "").maxAge(0).build().toString());
        return ApiResponse.ok(changed);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出登录")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(request, "").maxAge(0).build().toString());
        return ApiResponse.ok(true);
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

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    @Schema(description = "提醒邮箱变更请求")
    public record EmailChangeRequest(
            @Schema(description = "提醒邮箱；留空表示不接收提醒邮件", example = "zhangsan@example.com") String email) {
    }

    @Schema(description = "当前登录用户视图")
    public record AuthUserView(
            @Schema(description = "用户ID", example = "1") long id,
            @Schema(description = "用户名", example = "zhangsan") String username,
            @Schema(description = "用户角色", example = "ADMIN") String role,
            @Schema(description = "提醒邮箱", example = "zhangsan@example.com") String email) {
    }
}
