package com.fundpilot.backend.identityaccess.application.command.currentactor;

import com.fundpilot.backend.identityaccess.application.gateway.currentactor.ActorContext;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentActorCommandHandler {

    private final ActorContext actorContext;

    public ActorContext.Scope open(CurrentActor actor) {
        return actorContext.open(actor);
    }

    public void runAsSystem(long userId, Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("系统操作不能为空");
        }
        try (ActorContext.Scope ignored = actorContext.open(CurrentActor.system(userId))) {
            action.run();
        }
    }
}
