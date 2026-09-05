package com.fundpilot.backend.identityaccess.application.command.authentication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void enforcesLengthContentUsernameAndCurrentPasswordRules() {
        assertThatCode(() -> policy.validate("alice", "12345678901x", null)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("alice", "x".repeat(128), null)).doesNotThrowAnyException();
        assertViolation("alice", "12345678901");
        assertViolation("alice", "x".repeat(129));
        assertViolation("alice", "            ");
        assertViolation("same-password", "same-password");
        assertThatThrownBy(() -> policy.validate("alice", "current-password", "current-password"))
                .isInstanceOf(AuthenticationFailure.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((AuthenticationFailure) error).code())
                        .isEqualTo(AuthenticationFailure.Code.PASSWORD_POLICY_VIOLATION));
    }

    private void assertViolation(String username, String password) {
        assertThatThrownBy(() -> policy.validate(username, password, null))
                .isInstanceOf(AuthenticationFailure.class);
    }
}
