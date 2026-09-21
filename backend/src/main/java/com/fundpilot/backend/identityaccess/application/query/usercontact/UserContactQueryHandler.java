package com.fundpilot.backend.identityaccess.application.query.usercontact;

import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 供其它模块读取用户联系方式的只读查询。 */
@Service
@RequiredArgsConstructor
public class UserContactQueryHandler {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public Optional<String> emailOf(long userId) {
        return users.findById(userId).map(User::email);
    }
}
