package com.fundpilot.backend.identityaccess.application.query.currentactor;

public record CurrentActor(long userId, ActorRole role, boolean system) {
    public CurrentActor {
        if (userId <= 0) {
            throw new IllegalArgumentException("当前操作者必须关联真实用户");
        }
        if (role == null) {
            throw new IllegalArgumentException("当前操作者角色不能为空");
        }
    }

    public static CurrentActor user(long userId, ActorRole role) {
        return new CurrentActor(userId, role, false);
    }

    public static CurrentActor system(long userId) {
        return new CurrentActor(userId, ActorRole.ADMIN, true);
    }

    public boolean admin() {
        return role == ActorRole.ADMIN;
    }
}
