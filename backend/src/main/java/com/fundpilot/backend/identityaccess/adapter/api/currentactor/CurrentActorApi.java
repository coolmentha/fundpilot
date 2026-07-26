package com.fundpilot.backend.identityaccess.adapter.api.currentactor;

import com.fundpilot.backend.identityaccess.application.command.currentactor.CurrentActorCommandHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActorQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentActorApi {

    private final CurrentActorQueryHandler queries;
    private final CurrentActorCommandHandler commands;

    public Actor current() {
        var actor = queries.current();
        return new Actor(actor.userId(), ActorRole.valueOf(actor.role().name()), actor.system());
    }

    public long userId() {
        return current().userId();
    }

    public void runAsSystem(long userId, Runnable action) {
        commands.runAsSystem(userId, action);
    }

    public record Actor(long userId, ActorRole role, boolean system) {
        public boolean admin() {
            return role == ActorRole.ADMIN;
        }
    }

    public enum ActorRole {
        USER,
        ADMIN
    }
}
