package com.fundpilot.backend.user.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.user.entity.SiteUserEntity;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.repository.SiteUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {
    @Test
    void cannotDemoteLastEnabledAdmin() {
        SiteUserRepository repository = mock(SiteUserRepository.class);
        PasswordService passwordService = mock(PasswordService.class);
        SiteUserEntity admin = new SiteUserEntity();
        admin.setId(1L);
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.countByRoleAndEnabledTrue(UserRole.ADMIN)).thenReturn(1L);
        AdminUserService service = new AdminUserService(repository, passwordService);

        assertThatThrownBy(() -> service.updateRole(1L, UserRole.USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最后一个管理员");
        verifyNoInteractions(passwordService);
    }
}
