package com.fundpilot.backend.identityaccess.application.command.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.AuthenticationObservability;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.LegacyAccessKeyGateway;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.LoginRateLimiter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.PasswordHashGateway;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationCommandHandlerTest {

    private static final String SOURCE = "192.0.2.10";

    @Test
    void rateLimitedAttemptReturnsStableFailureWithoutLookingUpOrCheckingPassword() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        SessionTokenGateway sessions = mock(SessionTokenGateway.class);
        LegacyAccessKeyGateway legacyAccessKey = mock(LegacyAccessKeyGateway.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        AuthenticationObservability observability = mock(AuthenticationObservability.class);
        when(limiter.check(SOURCE, "alice")).thenReturn(new LoginRateLimiter.Decision(false, 30));
        var handler = new AuthenticationCommandHandler(users, passwords, sessions, legacyAccessKey,
                limiter, observability, new PasswordPolicy());

        assertThatThrownBy(() -> handler.login(" alice ", "wrong-password", null, SOURCE))
                .isInstanceOf(AuthenticationFailure.class)
                .satisfies(exception -> {
                    AuthenticationFailure failure = (AuthenticationFailure) exception;
                    assertThat(failure.code()).isEqualTo(AuthenticationFailure.Code.AUTH_RATE_LIMITED);
                    assertThat(failure.retryAfterSeconds()).isEqualTo(30);
                });

        verify(observability).loginRateLimited(SOURCE, "alice", 30);
        verify(observability).abnormalTraffic(SOURCE, "alice", "rate_limit");
        verifyNoInteractions(users, passwords, sessions, legacyAccessKey);
    }

    @Test
    void knownUserUsesStoredHashAndNormalizesUsername() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        SessionTokenGateway sessions = mock(SessionTokenGateway.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        AuthenticationObservability observability = mock(AuthenticationObservability.class);
        User user = User.rehydrate(7L, "alice", "stored-hash", UserRole.USER, true);
        when(limiter.check(SOURCE, "alice")).thenReturn(new LoginRateLimiter.Decision(true, 0));
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwords.matches("correct-password", "stored-hash")).thenReturn(true);
        when(sessions.issue(7L, UserRole.USER, 0)).thenReturn("session-token");
        var handler = new AuthenticationCommandHandler(users, passwords, sessions,
                mock(LegacyAccessKeyGateway.class), limiter, observability, new PasswordPolicy());

        var result = handler.login(" alice ", "correct-password", null, SOURCE);

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.sessionToken()).isEqualTo("session-token");
        verify(passwords).matches("correct-password", "stored-hash");
        verify(passwords, never()).matchesUnknown(any());
        verify(limiter).reset(SOURCE, "alice");
        verify(observability).loginSucceeded(SOURCE, "alice");
    }

    @Test
    void unknownUserRunsUnknownUserPasswordCheckAndReturnsSameUnauthorizedFailure() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        AuthenticationObservability observability = mock(AuthenticationObservability.class);
        when(limiter.check(SOURCE, "ghost")).thenReturn(new LoginRateLimiter.Decision(true, 0));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwords.matchesUnknown("wrong-password")).thenReturn(false);
        var handler = new AuthenticationCommandHandler(users, passwords, mock(SessionTokenGateway.class),
                mock(LegacyAccessKeyGateway.class), limiter, observability, new PasswordPolicy());

        assertThatThrownBy(() -> handler.login("ghost", "wrong-password", null, SOURCE))
                .isInstanceOf(AuthenticationFailure.class)
                .hasMessage("用户名或密码错误");

        verify(passwords).matchesUnknown("wrong-password");
        verify(passwords, never()).matches(any(), any());
        verify(observability).loginFailed(SOURCE, "ghost", "invalid_credentials");
    }

    @Test
    void disabledUserStillRunsPasswordCheckBeforeRejecting() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        AuthenticationObservability observability = mock(AuthenticationObservability.class);
        User user = User.rehydrate(8L, "disabled", "stored-hash", UserRole.USER, false);
        when(limiter.check(SOURCE, "disabled")).thenReturn(new LoginRateLimiter.Decision(true, 0));
        when(users.findByUsername("disabled")).thenReturn(Optional.of(user));
        when(passwords.matches("correct-password", "stored-hash")).thenReturn(true);
        var handler = new AuthenticationCommandHandler(users, passwords, mock(SessionTokenGateway.class),
                mock(LegacyAccessKeyGateway.class), limiter, observability, new PasswordPolicy());

        assertThatThrownBy(() -> handler.login("disabled", "correct-password", null, SOURCE))
                .isInstanceOf(AuthenticationFailure.class)
                .hasMessage("用户名或密码错误");

        verify(passwords).matches("correct-password", "stored-hash");
        verify(observability).loginFailed(SOURCE, "disabled", "invalid_credentials");
    }

    @Test
    void legacyKeyLoginRemainsAvailableWithoutConsumingUsernameLimiter() {
        UserRepository users = mock(UserRepository.class);
        SessionTokenGateway sessions = mock(SessionTokenGateway.class);
        LegacyAccessKeyGateway legacyAccessKey = mock(LegacyAccessKeyGateway.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        AuthenticationObservability observability = mock(AuthenticationObservability.class);
        User admin = User.rehydrate(1L, "admin", "hash", UserRole.ADMIN, true);
        when(legacyAccessKey.isConfigured()).thenReturn(true);
        when(legacyAccessKey.matches("legacy-key")).thenReturn(true);
        when(users.findFirstEnabledByRole(UserRole.ADMIN)).thenReturn(Optional.of(admin));
        when(sessions.issue(1L, UserRole.ADMIN, 0)).thenReturn("session-token");
        var handler = new AuthenticationCommandHandler(users, mock(PasswordHashGateway.class), sessions,
                legacyAccessKey, limiter, observability, new PasswordPolicy());

        var result = handler.login(null, null, "legacy-key", SOURCE);

        assertThat(result.userId()).isEqualTo(1L);
        verify(limiter, never()).check(any(), any());
        verify(observability).loginSucceeded(SOURCE, "admin");
    }
}
