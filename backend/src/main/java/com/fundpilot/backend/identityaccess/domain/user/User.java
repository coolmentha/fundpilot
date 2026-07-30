package com.fundpilot.backend.identityaccess.domain.user;

import java.util.Objects;

public final class User {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private UserRole role;
    private boolean enabled;

    private User(Long id, String username, String passwordHash, UserRole role, boolean enabled) {
        this.id = id;
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.enabled = enabled;
    }

    public static User create(String username, String passwordHash, UserRole role) {
        return new User(null, username, passwordHash, role, true);
    }

    public static User rehydrate(Long id, String username, String passwordHash,
                                 UserRole role, boolean enabled) {
        return new User(id, username, passwordHash, role, enabled);
    }

    public void changeRole(UserRole role) {
        this.role = Objects.requireNonNull(role);
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long id() {
        return id;
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
