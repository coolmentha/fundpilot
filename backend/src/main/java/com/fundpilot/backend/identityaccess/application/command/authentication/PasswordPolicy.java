package com.fundpilot.backend.identityaccess.application.command.authentication;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public void validate(String normalizedUsername, String candidate, String currentPassword) {
        int length = candidate == null ? 0 : candidate.codePointCount(0, candidate.length());
        boolean hasContent = candidate != null && candidate.codePoints()
                .anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c));
        if (length < 12 || length > 128 || !hasContent
                || candidate.equals(normalizedUsername) || candidate.equals(currentPassword)) {
            throw new AuthenticationFailure(AuthenticationFailure.Code.PASSWORD_POLICY_VIOLATION,
                    "新密码不符合密码规则");
        }
    }
}
