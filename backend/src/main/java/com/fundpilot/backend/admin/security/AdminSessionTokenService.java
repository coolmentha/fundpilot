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
        long expiresAt = clock.instant().plus(MAX_AGE).getEpochSecond();
        byte[] nonce = new byte[18];
        SECURE_RANDOM.nextBytes(nonce);
        String payload = expiresAt + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return payload + "." + sign(payload);
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank() || !configured()) {
            return false;
        }
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3) {
            return false;
        }
        try {
            long expiresAt = Long.parseLong(parts[0]);
            if (expiresAt <= clock.instant().getEpochSecond()) {
                return false;
            }
            String payload = parts[0] + "." + parts[1];
            return MessageDigest.isEqual(
                    sign(payload).getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.apiKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法创建管理会话签名", ex);
        }
    }

    private boolean configured() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }
}
