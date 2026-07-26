package com.fundpilot.backend.identityaccess.application.gateway.authentication;

public interface LegacyAccessKeyGateway {

    boolean isConfigured();

    boolean matches(String candidate);
}
