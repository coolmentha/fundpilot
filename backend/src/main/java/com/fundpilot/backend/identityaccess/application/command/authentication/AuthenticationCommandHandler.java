package com.fundpilot.backend.identityaccess.application.command.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.LegacyAccessKeyGateway;
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

    private final UserRepository users;
    private final PasswordHashGateway passwords;
    private final SessionTokenGateway sessions;
    private final LegacyAccessKeyGateway legacyAccessKey;

    @Transactional(readOnly = true)
    public LoginResult login(String username, String password, String suppliedLegacyKey) {
        if ((username == null || username.isBlank()) && suppliedLegacyKey != null) {
            if (!legacyAccessKey.isConfigured()) {
                throw new AuthenticationFailure(AuthenticationFailure.Code.ADMIN_AUTH_NOT_CONFIGURED,
                        "兼容管理密钥未配置");
            }
            if (legacyAccessKey.matches(suppliedLegacyKey)) {
                User admin = users.findFirstEnabledByRole(UserRole.ADMIN).orElseThrow(this::unauthorized);
                return result(admin.id(), admin.username(), admin.role());
            }
        }
        if (username == null || username.isBlank() || password == null) {
            throw unauthorized();
        }
        User user = users.findByUsername(username.trim()).orElseThrow(this::unauthorized);
        if (!user.enabled() || !passwords.matches(password, user.passwordHash())) {
            throw unauthorized();
        }
        return result(user.id(), user.username(), user.role());
    }

    private LoginResult result(long userId, String username, UserRole role) {
        return new LoginResult(userId, username, ActorRole.valueOf(role.name()), sessions.issue(userId, role));
    }

    private AuthenticationFailure unauthorized() {
        return new AuthenticationFailure(AuthenticationFailure.Code.ADMIN_UNAUTHORIZED, "用户名或密码错误");
    }

    public record LoginResult(long userId, String username, ActorRole role, String sessionToken) {
    }
}
