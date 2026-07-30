package com.fundpilot.backend.identityaccess.infrastructure.gateway.currentactor;

import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreadLocalActorContextTest {

    @Test
    void nestedScopesRestorePreviousActor() {
        ThreadLocalActorContext context = new ThreadLocalActorContext();
        CurrentActor outer = CurrentActor.user(1L, ActorRole.ADMIN);
        CurrentActor inner = CurrentActor.system(2L);

        try (var ignored = context.open(outer)) {
            assertThat(context.current()).isEqualTo(outer);
            try (var nested = context.open(inner)) {
                assertThat(context.current()).isEqualTo(inner);
            }
            assertThat(context.current()).isEqualTo(outer);
        }

        assertThatThrownBy(context::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未绑定操作者");
    }

    @Test
    void actorCannotUseZeroAsGlobalIdentity() {
        assertThatThrownBy(() -> CurrentActor.system(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("真实用户");
    }
}
