package com.fundpilot.backend.identityaccess.application.gateway.authentication;

public interface PasswordHashGateway {

    String hash(String password);

    boolean matches(String password, String encoded);

    boolean matchesUnknown(String password);
}
