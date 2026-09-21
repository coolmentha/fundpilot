package com.fundpilot.backend.identityaccess.adapter.api.usercontact;

import com.fundpilot.backend.identityaccess.application.query.usercontact.UserContactQueryHandler;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 其它模块读取用户联系方式（提醒邮件收件人）的只读入口。 */
@Component
@RequiredArgsConstructor
public class UserContactApi {

    private final UserContactQueryHandler queries;

    /** @return 该用户已配置的提醒邮箱；未配置时返回空。 */
    public Optional<String> emailOf(long userId) {
        return queries.emailOf(userId);
    }
}
