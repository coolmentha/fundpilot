package com.fundpilot.backend.identityaccess.application.query.currentactor;

import com.fundpilot.backend.identityaccess.application.gateway.currentactor.ActorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentActorQueryHandler {

    private final ActorContext actorContext;

    public CurrentActor current() {
        return actorContext.current();
    }
}
