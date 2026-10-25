package com.fundpilot.backend.identityaccess.application.query.opentoken;

import com.fundpilot.backend.identityaccess.application.gateway.opentoken.OpenApiTokenGateway;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiToken;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenHash;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenApiTokenQueryHandler {

    private static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final OpenApiTokenRepository tokens;
    private final OpenApiTokenGateway tokenText;

    @Transactional(readOnly = true)
    public Optional<TokenStatus> findStatus(long ownerId) {
        return tokens.findActiveByOwner(ownerId).map(OpenApiTokenQueryHandler::status);
    }

    public Optional<Long> findOwnerId(String plaintext) {
        return tokenText.findOwnerId(plaintext);
    }

    /** 高频 Open API 轮询不应对每次 GET 都写库；最多每分钟刷新一次最近使用时间。 */
    @Transactional
    public void markUsed(String plaintext, Instant now) {
        tokens.markUsed(OpenApiTokenHash.of(plaintext), now, now.minus(LAST_USED_WRITE_INTERVAL));
    }

    private static TokenStatus status(OpenApiToken token) {
        return new TokenStatus(true, token.rotatedAt(), token.lastUsedAt());
    }

    public record TokenStatus(boolean active, Instant rotatedAt, Instant lastUsedAt) {
    }
}
