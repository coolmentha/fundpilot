package com.fundpilot.backend.identityaccess.application.query.userdirectory;

import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDirectoryQueryHandler {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<Long> activeUserIds() {
        return users.findAll().stream()
                .filter(User::enabled)
                .map(User::id)
                .toList();
    }
}
