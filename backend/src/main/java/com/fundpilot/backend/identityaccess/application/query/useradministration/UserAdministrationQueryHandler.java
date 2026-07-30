package com.fundpilot.backend.identityaccess.application.query.useradministration;

import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationFailure;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdministrationQueryHandler {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<UserResult> list(CurrentActor actor) {
        if (actor == null || !actor.admin()) {
            throw new UserAdministrationFailure(UserAdministrationFailure.Code.ADMIN_FORBIDDEN,
                    "需要管理员权限");
        }
        return users.findAll().stream().map(UserResult::from).toList();
    }

    public record UserResult(long id, String username, Role role, boolean enabled) {
        private static UserResult from(User user) {
            return new UserResult(user.id(), user.username(), Role.valueOf(user.role().name()), user.enabled());
        }
    }

    public enum Role {
        USER,
        ADMIN
    }
}
