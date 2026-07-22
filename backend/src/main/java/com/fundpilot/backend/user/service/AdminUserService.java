package com.fundpilot.backend.user.service;

import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.user.entity.SiteUserEntity;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.repository.SiteUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final SiteUserRepository repository;
    private final PasswordService passwordService;

    @Transactional(readOnly = true)
    public List<SiteUserEntity> list() {
        return repository.findAll();
    }

    @Transactional
    public SiteUserEntity create(String username, String password, UserRole role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ErrorCode.USER_ACCOUNT_INVALID.toException("用户名和密码不能为空");
        }
        String normalizedUsername = username.trim();
        if (repository.findByUsername(normalizedUsername).isPresent()) {
            throw ErrorCode.USER_ACCOUNT_INVALID.toException("用户名已存在");
        }
        SiteUserEntity user = new SiteUserEntity();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordService.hash(password));
        user.setRole(role == null ? UserRole.USER : role);
        user.setEnabled(true);
        return repository.save(user);
    }

    @Transactional
    public SiteUserEntity updateStatus(Long id, boolean enabled) {
        SiteUserEntity user = requireUser(id);
        ensureAdminRemains(user, enabled, user.getRole());
        user.setEnabled(enabled);
        return repository.save(user);
    }

    @Transactional
    public SiteUserEntity updateRole(Long id, UserRole role) {
        if (role == null) throw ErrorCode.USER_ACCOUNT_INVALID.toException("角色不能为空");
        SiteUserEntity user = requireUser(id);
        ensureAdminRemains(user, user.isEnabled(), role);
        user.setRole(role);
        return repository.save(user);
    }

    private SiteUserEntity requireUser(Long id) {
        return repository.findById(id).orElseThrow(
                () -> ErrorCode.ENTITY_NOT_FOUND.toException("用户不存在"));
    }

    private void ensureAdminRemains(SiteUserEntity user, boolean enabled, UserRole role) {
        if (user.isEnabled() && user.getRole() == UserRole.ADMIN
                && (!enabled || role != UserRole.ADMIN)
                && repository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw ErrorCode.ADMIN_FORBIDDEN.toException("不能移除最后一个管理员");
        }
    }
}
