package com.fundpilot.backend.identityaccess.application.command.useradministration;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.PasswordHashGateway;
import com.fundpilot.backend.identityaccess.application.command.authentication.PasswordPolicy;
import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;
import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserAdministrationCommandHandlerTest {

    @Test
    void cannotDemoteLastEnabledAdmin() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        User admin = User.rehydrate(1L, "admin", "hash", UserRole.ADMIN, true);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.lockFirstEnabledByRole(UserRole.ADMIN)).thenReturn(Optional.of(admin));
        when(users.countEnabledByRole(UserRole.ADMIN)).thenReturn(1L);
        var handler = new UserAdministrationCommandHandler(users, passwords, new PasswordPolicy());

        assertThatThrownBy(() -> handler.updateRole(CurrentActor.user(1L, ActorRole.ADMIN), 1L,
                UserAdministrationCommandHandler.Role.USER))
                .isInstanceOf(UserAdministrationFailure.class)
                .hasMessageContaining("最后一个管理员");
        verifyNoInteractions(passwords);
    }

    @Test
    void cannotDisableLastEnabledAdmin() {
        UserRepository users = mock(UserRepository.class);
        User admin = User.rehydrate(1L, "admin", "hash", UserRole.ADMIN, true);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.lockFirstEnabledByRole(UserRole.ADMIN)).thenReturn(Optional.of(admin));
        when(users.countEnabledByRole(UserRole.ADMIN)).thenReturn(1L);
        var handler = new UserAdministrationCommandHandler(users, mock(PasswordHashGateway.class), new PasswordPolicy());

        assertThatThrownBy(() -> handler.updateStatus(CurrentActor.user(1L, ActorRole.ADMIN), 1L, false))
                .isInstanceOf(UserAdministrationFailure.class)
                .hasMessageContaining("最后一个管理员");
    }

    @Test
    void regularUserCannotManageUsers() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        var handler = new UserAdministrationCommandHandler(users, passwords, new PasswordPolicy());

        assertThatThrownBy(() -> handler.create(CurrentActor.user(2L, ActorRole.USER),
                "new-user", "password", UserAdministrationCommandHandler.Role.USER))
                .isInstanceOf(UserAdministrationFailure.class)
                .hasMessageContaining("管理员权限");
        verifyNoInteractions(users, passwords);
    }

    @Test
    void administratorCreationUsesSharedPasswordPolicy() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        var handler = new UserAdministrationCommandHandler(users, passwords, new PasswordPolicy());

        assertThatThrownBy(() -> handler.create(CurrentActor.user(1L, ActorRole.ADMIN),
                "new-user", "too-short", UserAdministrationCommandHandler.Role.USER))
                .isInstanceOf(AuthenticationFailure.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((AuthenticationFailure) error).code())
                        .isEqualTo(AuthenticationFailure.Code.PASSWORD_POLICY_VIOLATION));
        assertThatThrownBy(() -> handler.create(CurrentActor.user(1L, ActorRole.ADMIN),
                "new-user", "", UserAdministrationCommandHandler.Role.USER))
                .isInstanceOf(AuthenticationFailure.class)
                .satisfies(error -> assertThat(((AuthenticationFailure) error).code())
                        .isEqualTo(AuthenticationFailure.Code.PASSWORD_POLICY_VIOLATION));
        verifyNoInteractions(users, passwords);
    }

    @Test
    void existingBootstrapAdminDoesNotRevalidateConfiguredPassword() {
        UserRepository users = mock(UserRepository.class);
        PasswordHashGateway passwords = mock(PasswordHashGateway.class);
        User admin = User.rehydrate(1L, "admin", "hash", UserRole.ADMIN, true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        var handler = new UserAdministrationCommandHandler(users, passwords, new PasswordPolicy());

        assertThat(handler.ensureBootstrapAdmin("admin", "legacy").id()).isEqualTo(1L);
        verifyNoInteractions(passwords);
    }
}
