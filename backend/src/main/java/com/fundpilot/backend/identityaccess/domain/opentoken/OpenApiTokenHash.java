package com.fundpilot.backend.identityaccess.domain.opentoken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** 用户级 Open API Token 摘要，领域层不保留可分发的明文 Token。 */
public record OpenApiTokenHash(String value) {

    public OpenApiTokenHash {
        Objects.requireNonNull(value);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Token 摘要必须是 64 位小写十六进制 SHA-256");
        }
    }

    /** SHA-256 足以用于高熵随机 Token 的不可逆存储，避免引入额外密码哈希开销。 */
    public static OpenApiTokenHash of(String plaintext) {
        Objects.requireNonNull(plaintext);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return new OpenApiTokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }
}
