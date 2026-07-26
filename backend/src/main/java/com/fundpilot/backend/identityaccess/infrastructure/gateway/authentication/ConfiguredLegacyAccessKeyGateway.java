package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.LegacyAccessKeyGateway;
import com.fundpilot.backend.identityaccess.infrastructure.configuration.IdentityAccessProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfiguredLegacyAccessKeyGateway implements LegacyAccessKeyGateway {

    private final IdentityAccessProperties properties;

    @Override
    public boolean isConfigured() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    @Override
    public boolean matches(String candidate) {
        if (candidate == null || !isConfigured()) {
            return false;
        }
        return MessageDigest.isEqual(properties.apiKey().getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
