package com.fundpilot.backend.identityaccess.application.gateway.authentication;

import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import java.time.Duration;
import java.util.Optional;

public interface SessionTokenGateway {

    Duration maxAge();

    String issue(long userId, UserRole role);

    Optional<SessionIdentity> parse(String token);

    record SessionIdentity(long userId, UserRole role) {
    }
}
