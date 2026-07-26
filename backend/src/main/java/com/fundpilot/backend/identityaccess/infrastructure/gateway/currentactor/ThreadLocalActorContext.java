package com.fundpilot.backend.identityaccess.infrastructure.gateway.currentactor;

import com.fundpilot.backend.identityaccess.application.gateway.currentactor.ActorContext;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import org.springframework.stereotype.Component;

@Component
public class ThreadLocalActorContext implements ActorContext {

    private final ThreadLocal<CurrentActor> actors = new ThreadLocal<>();

    @Override
    public CurrentActor current() {
        CurrentActor actor = actors.get();
        if (actor == null) {
            throw new IllegalStateException("当前执行线程未绑定操作者");
        }
        return actor;
    }

    @Override
    public Scope open(CurrentActor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("当前操作者不能为空");
        }
        CurrentActor previous = actors.get();
        actors.set(actor);
        return () -> {
            if (previous == null) {
                actors.remove();
            } else {
                actors.set(previous);
            }
        };
    }
}
