package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.PasswordHashGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class Pbkdf2PasswordHashGateway implements PasswordHashGateway {

    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encode(salt, derive(password, salt));
    }

    @Override
    public boolean matches(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$", 3);
            if (parts.length != 3) {
                return false;
            }
            byte[] salt = Base64.getUrlDecoder().decode(parts[1]);
            return MessageDigest.isEqual(derive(password, salt),
                    Base64.getUrlDecoder().decode(parts[2]));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String encode(byte[] salt, byte[] hash) {
        return "pbkdf2$" + Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private byte[] derive(String password, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS))
                    .getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("密码哈希失败", ex);
        }
    }
}
