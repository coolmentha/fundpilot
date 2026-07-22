package com.fundpilot.backend.admin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import com.fundpilot.backend.user.entity.UserRole;

@Service
@RequiredArgsConstructor
public class AdminSessionTokenService {

    public static final String COOKIE_NAME = "fundpilot_session";
    public static final Duration MAX_AGE = Duration.ofDays(30);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AdminApiKeyProperties properties;
    private final Clock clock;

    public String issue() {
        return issue(0L, UserRole.ADMIN);
    }

    public String issue(long userId, UserRole role) {
        long expiresAt = clock.instant().plus(MAX_AGE).getEpochSecond();
        byte[] nonce = new byte[18];
        SECURE_RANDOM.nextBytes(nonce);
        String payload = expiresAt + "." + userId + "." + role.name() + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return payload + "." + sign(payload);
    }

    public boolean isValid(String token) {
        return parse(token).isPresent();
    }

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
            boolean valid = MessageDigest.isEqual(
                    sign(payload).getBytes(StandardCharsets.US_ASCII),
                    parts[4].getBytes(StandardCharsets.US_ASCII));
            if (!valid) return Optional.empty();
            return Optional.of(new SessionIdentity(Long.parseLong(parts[1]), UserRole.valueOf(parts[2])));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record SessionIdentity(long userId, UserRole role) {}

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
