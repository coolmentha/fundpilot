package com.fundpilot.backend.identityaccess.application.query.authentication;

import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.LegacyAccessKeyGateway;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationQueryHandler {

    private final UserRepository users;
    private final SessionTokenGateway sessions;
    private final LegacyAccessKeyGateway legacyAccessKey;

    @Transactional(readOnly = true)
    public Optional<AuthenticatedActor> authenticate(String suppliedKey, String sessionToken) {
        if (legacyAccessKey.matches(suppliedKey)) {
            return users.findFirstEnabledByRole(UserRole.ADMIN).map(this::actor);
        }
        return sessions.parse(sessionToken).flatMap(this::activeUser);
    }

    @Transactional(readOnly = true)
    public AuthenticatedActor requireActive(long userId) {
        User user = users.findById(userId).filter(User::enabled)
                .orElseThrow(() -> new AuthenticationFailure(
                        AuthenticationFailure.Code.ADMIN_UNAUTHORIZED, "用户已停用"));
        return actor(user);
    }

    public boolean legacyKeyConfigured() {
        return legacyAccessKey.isConfigured();
    }

    private Optional<AuthenticatedActor> activeUser(SessionTokenGateway.SessionIdentity identity) {
        return users.findById(identity.userId()).filter(User::enabled).map(this::actor);
    }

    private AuthenticatedActor actor(User user) {
        return new AuthenticatedActor(user.id(), user.username(), ActorRole.valueOf(user.role().name()));
    }

    public record AuthenticatedActor(long userId, String username, ActorRole role) {
    }
}
