package com.fundpilot.backend.identityaccess.application.command.opentoken;

import com.fundpilot.backend.identityaccess.application.gateway.opentoken.OpenApiTokenGateway;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiToken;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenHash;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户级 Token 命令。刷新即轮换，不延长旧 Token 有效期。 */
@Service
@RequiredArgsConstructor
public class OpenApiTokenCommandHandler {

    private final OpenApiTokenRepository tokens;
    private final OpenApiTokenGateway tokenText;
    private final Clock clock;

    @Transactional
    public IssuedToken issueOrRotate(long ownerId) {
        Instant now = clock.instant();
        return tokens.findActiveByOwnerForUpdate(ownerId)
                .map(existing -> rotate(existing, now))
                .orElseGet(() -> issue(ownerId, now));
    }

    @Transactional
    public void revoke(long ownerId) {
        tokens.revokeActive(ownerId);
    }

    private IssuedToken issue(long ownerId, Instant now) {
        String plaintext = tokenText.generate();
        OpenApiToken saved = tokens.save(OpenApiToken.issue(ownerId, OpenApiTokenHash.of(plaintext), now));
        return new IssuedToken(saved.id(), plaintext, saved.rotatedAt());
    }

    private IssuedToken rotate(OpenApiToken existing, Instant now) {
        String plaintext = tokenText.generate();
        OpenApiToken saved = tokens.save(existing.rotateTo(OpenApiTokenHash.of(plaintext), now));
        return new IssuedToken(saved.id(), plaintext, saved.rotatedAt());
    }

    public record IssuedToken(long id, String token, Instant rotatedAt) {
    }
}
