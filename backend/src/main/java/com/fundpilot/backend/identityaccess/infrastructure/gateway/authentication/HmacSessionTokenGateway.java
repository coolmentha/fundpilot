package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.identityaccess.infrastructure.configuration.IdentityAccessProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HmacSessionTokenGateway implements SessionTokenGateway {

    public static final Duration MAX_AGE = Duration.ofDays(30);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityAccessProperties properties;
    private final Clock clock;

    @Override
    public Duration maxAge() {
        return MAX_AGE;
    }

    @Override
    public String issue(long userId, UserRole role) {
        if (userId <= 0) {
            throw new IllegalArgumentException("会话必须关联真实用户");
        }
        long expiresAt = clock.instant().plus(MAX_AGE).getEpochSecond();
        byte[] nonce = new byte[18];
        RANDOM.nextBytes(nonce);
        String payload = expiresAt + "." + userId + "." + role.name() + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return payload + "." + sign(payload);
    }

    @Override
    public Optional<SessionIdentity> parse(String token) {
        if (token == null || token.isBlank() || signingSecret() == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", 5);
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            long expiresAt = Long.parseLong(parts[0]);
            if (expiresAt <= clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                    parts[4].getBytes(StandardCharsets.US_ASCII))) {
                return Optional.empty();
            }
            long userId = Long.parseLong(parts[1]);
            if (userId <= 0) {
                return Optional.empty();
            }
            return Optional.of(new SessionIdentity(userId, UserRole.valueOf(parts[2])));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法创建管理会话签名", ex);
        }
    }

    private String signingSecret() {
        if (properties.sessionSecret() != null && !properties.sessionSecret().isBlank()) {
            return properties.sessionSecret();
        }
        return properties.apiKey() == null || properties.apiKey().isBlank() ? null : properties.apiKey();
    }
}
