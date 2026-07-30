package com.fundpilot.backend.identityaccess.application.command.useradministration;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.PasswordHashGateway;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdministrationCommandHandler {

    private final UserRepository users;
    private final PasswordHashGateway passwords;

    @Transactional
    public UserResult create(CurrentActor actor, String username, String password, Role role) {
        requireAdmin(actor);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw invalid("用户名和密码不能为空");
        }
        String normalized = username.trim();
        if (users.findByUsername(normalized).isPresent()) {
            throw invalid("用户名已存在");
        }
        UserRole domainRole = role == null ? UserRole.USER : UserRole.valueOf(role.name());
        return UserResult.from(users.save(User.create(normalized, passwords.hash(password), domainRole)));
    }

    @Transactional
    public UserResult ensureBootstrapAdmin(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw invalid("初始管理员用户名和密码不能为空");
        }
        String normalized = username.trim();
        return users.findByUsername(normalized)
                .map(UserResult::from)
                .orElseGet(() -> UserResult.from(users.save(User.create(normalized,
                        passwords.hash(password), UserRole.ADMIN))));
    }

    @Transactional
    public UserResult updateStatus(CurrentActor actor, long id, boolean enabled) {
        requireAdmin(actor);
        User user = requireUser(id);
        ensureAdminRemains(user, enabled, user.role());
        user.changeEnabled(enabled);
        return UserResult.from(users.save(user));
    }

    @Transactional
    public UserResult updateRole(CurrentActor actor, long id, Role role) {
        requireAdmin(actor);
        if (role == null) {
            throw invalid("角色不能为空");
        }
        User user = requireUser(id);
        UserRole domainRole = UserRole.valueOf(role.name());
        ensureAdminRemains(user, user.enabled(), domainRole);
        user.changeRole(domainRole);
        return UserResult.from(users.save(user));
    }

    private User requireUser(long id) {
        return users.findById(id).orElseThrow(() -> new UserAdministrationFailure(
                UserAdministrationFailure.Code.USER_NOT_FOUND, "用户不存在"));
    }

    private void ensureAdminRemains(User user, boolean enabled, UserRole role) {
        if (user.enabled() && user.role() == UserRole.ADMIN
                && (!enabled || role != UserRole.ADMIN)
                && users.countEnabledByRole(UserRole.ADMIN) <= 1) {
            throw new UserAdministrationFailure(UserAdministrationFailure.Code.ADMIN_FORBIDDEN,
                    "不能移除最后一个管理员");
        }
    }

    private UserAdministrationFailure invalid(String message) {
        return new UserAdministrationFailure(UserAdministrationFailure.Code.USER_ACCOUNT_INVALID, message);
    }

    private void requireAdmin(CurrentActor actor) {
        if (actor == null || !actor.admin()) {
            throw new UserAdministrationFailure(UserAdministrationFailure.Code.ADMIN_FORBIDDEN,
                    "需要管理员权限");
        }
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
