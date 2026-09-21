package com.fundpilot.backend.identityaccess.application.command.authentication;

import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 提醒邮箱校验。留空表示不接收提醒邮件；非空时须形如 {@code local@domain}。 */
@Component
public class EmailPolicy {

    private static final int MAX_LENGTH = 255;

    /** 规范化并校验邮箱；传入 null/空白返回 null（表示清除邮箱）。 */
    public String normalize(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!valid(normalized)) {
            throw new BusinessException(ErrorCode.USER_EMAIL_INVALID, "邮箱格式不正确");
        }
        return normalized;
    }

    private static boolean valid(String email) {
        if (email.length() > MAX_LENGTH || email.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@')) {
            return false;
        }
        String domain = email.substring(at + 1);
        int dot = domain.indexOf('.');
        return dot > 0 && dot < domain.length() - 1;
    }
}
