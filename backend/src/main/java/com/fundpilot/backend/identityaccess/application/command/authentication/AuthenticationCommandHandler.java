package com.fundpilot.backend.identityaccess.application.command.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.LegacyAccessKeyGateway;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.AuthenticationObservability;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.LoginRateLimiter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.PasswordHashGateway;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationCommandHandler {

    private static final String INVALID_CREDENTIALS_REASON = "invalid_credentials";
    private static final String RATE_LIMIT_REASON = "rate_limit";

    private final UserRepository users;
    private final PasswordHashGateway passwords;
    private final SessionTokenGateway sessions;
    private final LegacyAccessKeyGateway legacyAccessKey;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthenticationObservability observability;
    private final PasswordPolicy passwordPolicy;

    @Transactional(readOnly = true)
    public LoginResult login(String username, String password, String suppliedLegacyKey) {
        return login(username, password, suppliedLegacyKey, "unknown");
    }

    @Transactional(readOnly = true)
    public LoginResult login(String username, String password, String suppliedLegacyKey, String source) {
        if ((username == null || username.isBlank()) && suppliedLegacyKey != null) {
            if (!legacyAccessKey.isConfigured()) {
                throw new AuthenticationFailure(AuthenticationFailure.Code.ADMIN_AUTH_NOT_CONFIGURED,
                        "兼容管理密钥未配置");
            }
            if (legacyAccessKey.matches(suppliedLegacyKey)) {
                User admin = users.findFirstEnabledByRole(UserRole.ADMIN).orElseThrow(this::unauthorized);
                LoginResult result = result(admin);
                observability.loginSucceeded(source, admin.username());
                return result;
            }
        }
        String normalizedUsername = normalizeUsername(username);
        LoginRateLimiter.Decision decision = loginRateLimiter.check(source, normalizedUsername);
        if (!decision.allowed()) {
            observability.loginRateLimited(source, normalizedUsername, decision.retryAfterSeconds());
            observability.abnormalTraffic(source, normalizedUsername, RATE_LIMIT_REASON);
            throw new AuthenticationFailure(AuthenticationFailure.Code.AUTH_RATE_LIMITED,
                    "登录尝试过于频繁，请稍后重试", decision.retryAfterSeconds());
        }
        String candidatePassword = password == null ? "" : password;
        User user = normalizedUsername.isBlank() ? null
                : users.findByUsername(normalizedUsername).orElse(null);
        boolean passwordMatches = user == null
                ? passwords.matchesUnknown(candidatePassword)
                : passwords.matches(candidatePassword, user.passwordHash());
        if (user == null || !user.enabled() || !passwordMatches) {
            observability.loginFailed(source, normalizedUsername, INVALID_CREDENTIALS_REASON);
            throw unauthorized();
        }
        LoginResult result = result(user);
        loginRateLimiter.reset(source, normalizedUsername);
        observability.loginSucceeded(source, normalizedUsername);
        return result;
    }

    @Transactional
    public boolean changePassword(long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).filter(User::enabled).orElseThrow(this::unauthorized);
        if (currentPassword == null || !passwords.matches(currentPassword, user.passwordHash())) {
            throw new AuthenticationFailure(AuthenticationFailure.Code.CURRENT_PASSWORD_INVALID, "当前密码无效");
        }
        passwordPolicy.validate(user.username(), newPassword, currentPassword);
        user.changePasswordHash(passwords.hash(newPassword));
        users.save(user);
        return true;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private LoginResult result(User user) {
        long userVersion = user.version() == null ? 0L : user.version();
        return new LoginResult(user.id(), user.username(), ActorRole.valueOf(user.role().name()),
                sessions.issue(user.id(), user.role(), userVersion));
    }

    private AuthenticationFailure unauthorized() {
        return new AuthenticationFailure(AuthenticationFailure.Code.ADMIN_UNAUTHORIZED, "用户名或密码错误");
    }

    public record LoginResult(long userId, String username, ActorRole role, String sessionToken) {
    }
}
