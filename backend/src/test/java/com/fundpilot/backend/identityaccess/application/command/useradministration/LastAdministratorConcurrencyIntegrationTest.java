package com.fundpilot.backend.identityaccess.application.command.useradministration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.CreateUserRequest;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.Role;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LastAdministratorConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private UserAdministrationApi users;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentDisableAndDemotionAlwaysLeaveOneEnabledAdministrator() throws Exception {
        jdbc.update("DELETE FROM site_user");
        String suffix = UUID.randomUUID().toString();
        var first = users.ensureBootstrapAdmin("admin-a-" + suffix, "integration-test-password");
        Actor actor = new Actor(first.id(), ActorRole.ADMIN, false);
        var second = users.create(actor,
                new CreateUserRequest("admin-b-" + suffix, "integration-test-password", Role.ADMIN));

        assertThat(race(
                () -> users.updateStatus(actor, first.id(), false),
                () -> users.updateStatus(actor, second.id(), false)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(userRepository.countEnabledByRole(UserRole.ADMIN)).isEqualTo(1);

        users.updateStatus(actor, first.id(), true);
        users.updateStatus(actor, second.id(), true);
        assertThat(userRepository.countEnabledByRole(UserRole.ADMIN)).isEqualTo(2);

        assertThat(race(
                () -> users.updateRole(actor, first.id(), Role.USER),
                () -> users.updateRole(actor, second.id(), Role.USER)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(userRepository.countEnabledByRole(UserRole.ADMIN)).isEqualTo(1);
    }

    private List<Boolean> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(atStart(ready, start, first));
            Future<Boolean> secondResult = executor.submit(atStart(ready, start, second));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<Boolean> atStart(CountDownLatch ready, CountDownLatch start, Runnable action) {
        return () -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                action.run();
                return true;
            } catch (UserAdministrationFailure failure) {
                assertThat(failure.code()).isEqualTo(UserAdministrationFailure.Code.ADMIN_FORBIDDEN);
                return false;
            }
        };
    }
}
