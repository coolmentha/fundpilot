package com.fundpilot.backend.identityaccess.infrastructure.gateway.opentoken;

import com.fundpilot.backend.identityaccess.application.gateway.opentoken.OpenApiTokenGateway;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenHash;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OpenApiTokenGatewayImpl implements OpenApiTokenGateway {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OpenApiTokenRepository tokens;

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return "fp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public Optional<Long> findOwnerId(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return Optional.empty();
        }
        return tokens.findActiveByHash(OpenApiTokenHash.of(plaintext)).map(token -> token.ownerId());
    }
}
