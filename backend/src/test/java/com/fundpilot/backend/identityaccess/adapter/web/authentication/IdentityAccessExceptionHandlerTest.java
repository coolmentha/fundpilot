package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityAccessExceptionHandlerTest {

    private final IdentityAccessExceptionHandler handler = new IdentityAccessExceptionHandler();

    @Test
    void rateLimitedFailureMapsTo429WithRetryAfterAndStableCode() {
        var response = handler.authentication(new AuthenticationFailure(
                AuthenticationFailure.Code.AUTH_RATE_LIMITED,
                "登录尝试过于频繁，请稍后重试", 30));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(response.getBody().code()).isEqualTo("AUTH_RATE_LIMITED");
        assertThat(response.getBody().message()).doesNotContain("alice");
    }

    @Test
    void ordinaryUnauthorizedFailureHasNoRetryAfterHeader() {
        var response = handler.authentication(new AuthenticationFailure(
                AuthenticationFailure.Code.ADMIN_UNAUTHORIZED, "用户名或密码错误"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        assertThat(response.getBody().code()).isEqualTo("ADMIN_UNAUTHORIZED");
    }
}
