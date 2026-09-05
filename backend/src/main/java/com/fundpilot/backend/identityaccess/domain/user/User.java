package com.fundpilot.backend.identityaccess.domain.user;

import java.util.Objects;

public final class User {

    private final Long id;
    private final Long version;
    private final String username;
    private String passwordHash;
    private UserRole role;
    private boolean enabled;

    private User(Long id, Long version, String username, String passwordHash, UserRole role, boolean enabled) {
        this.id = id;
        this.version = version;
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.enabled = enabled;
    }

    public static User create(String username, String passwordHash, UserRole role) {
        return new User(null, null, username, passwordHash, role, true);
    }

    public static User rehydrate(Long id, String username, String passwordHash,
                                 UserRole role, boolean enabled) {
        return rehydrate(id, 0L, username, passwordHash, role, enabled);
    }

    public static User rehydrate(Long id, Long version, String username, String passwordHash,
                                 UserRole role, boolean enabled) {
        return new User(id, version, username, passwordHash, role, enabled);
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
}
