package com.fundpilot.backend.identityaccess.domain.user;

import java.util.Objects;

public final class User {

    private final Long id;
    private final Long version;
    private final String username;
    private String passwordHash;
    private UserRole role;
    private boolean enabled;
    private String email;

    private User(Long id, Long version, String username, String passwordHash, UserRole role, boolean enabled,
                 String email) {
        this.id = id;
        this.version = version;
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.enabled = enabled;
        this.email = email;
    }

    public static User create(String username, String passwordHash, UserRole role) {
        return new User(null, null, username, passwordHash, role, true, null);
    }

    public static User rehydrate(Long id, String username, String passwordHash,
                                 UserRole role, boolean enabled, String email) {
        return rehydrate(id, 0L, username, passwordHash, role, enabled, email);
    }

    public static User rehydrate(Long id, Long version, String username, String passwordHash,
                                 UserRole role, boolean enabled, String email) {
        return new User(id, version, username, passwordHash, role, enabled, email);
    }

    public void changeRole(UserRole role) {
        this.role = Objects.requireNonNull(role);
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    /** 传入 null/空白表示清除邮箱;非空值由应用层完成规范化与格式校验。 */
    public void changeEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email;
    }

    public Long id() {
        return id;
    }

    public Long version() {
        return version;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserRole role() {
        return role;
    }

    public boolean enabled() {
        return enabled;
    }

    public String email() {
        return email;
    }
}
