package com.fundpilot.backend.integration.yangjibao;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class YangjibaoSigner {
    public String anonymous(String path, long timestamp, String secret) {
        return md5(pathOnly(path) + timestamp + secret);
    }

    public String authenticated(String path, String token, long timestamp, String secret) {
        return md5(pathOnly(path) + token + timestamp + secret);
    }

    private String pathOnly(String path) {
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    private String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
