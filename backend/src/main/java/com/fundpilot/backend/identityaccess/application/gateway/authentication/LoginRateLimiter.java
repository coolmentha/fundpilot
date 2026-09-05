package com.fundpilot.backend.identityaccess.application.gateway.authentication;

public interface LoginRateLimiter {

    Decision check(String source, String normalizedUsername);

    void reset(String source, String normalizedUsername);

    record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
