package com.fundpilot.backend.identityaccess.adapter.api.useradministration;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationCommandHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import com.fundpilot.backend.identityaccess.application.query.useradministration.UserAdministrationQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAdministrationApi {

    private final UserAdministrationCommandHandler commands;
    private final UserAdministrationQueryHandler queries;

    public List<UserResult> list(Actor actor) {
        return queries.list(toApplication(actor)).stream()
                .map(user -> new UserResult(user.id(), user.username(), Role.valueOf(user.role().name()),
                        user.enabled()))
                .toList();
    }

    public UserResult create(Actor actor, CreateUserRequest request) {
        return from(commands.create(toApplication(actor), request.username(), request.password(),
                request.role() == null ? null
                        : UserAdministrationCommandHandler.Role.valueOf(request.role().name())));
    }

    public UserResult ensureBootstrapAdmin(String username, String password) {
        return from(commands.ensureBootstrapAdmin(username, password));
    }

    public UserResult updateStatus(Actor actor, long userId, boolean enabled) {
        return from(commands.updateStatus(toApplication(actor), userId, enabled));
    }

    public UserResult updateRole(Actor actor, long userId, Role role) {
        return from(commands.updateRole(toApplication(actor), userId,
                role == null ? null : UserAdministrationCommandHandler.Role.valueOf(role.name())));
    }

    private UserResult from(UserAdministrationCommandHandler.UserResult user) {
        return new UserResult(user.id(), user.username(), Role.valueOf(user.role().name()), user.enabled());
    }

    private CurrentActor toApplication(Actor actor) {
        return actor == null ? null : CurrentActor.user(actor.userId(),
                ActorRole.valueOf(actor.role().name()));
    }

    public record CreateUserRequest(String username, String password, Role role) {
    }

    public record UserResult(long id, String username, Role role, boolean enabled) {
    }

    public enum Role {
        USER,
        ADMIN
    }
}
