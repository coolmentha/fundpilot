package com.fundpilot.backend.identityaccess.application.gateway.currentactor;

import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;

public interface ActorContext {

    CurrentActor current();

    Scope open(CurrentActor actor);

    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
