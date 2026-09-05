package com.fundpilot.backend.identityaccess.application.gateway.authentication;

public interface AuthenticationObservability {

    void loginSucceeded(String source, String normalizedUsername);

    void loginFailed(String source, String normalizedUsername, String reason);

    void loginRateLimited(String source, String normalizedUsername, long retryAfterSeconds);

    void abnormalTraffic(String source, String normalizedUsername, String reason);
}
