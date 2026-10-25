package com.fundpilot.backend.identityaccess.domain.opentoken;

import java.time.Instant;
import java.util.Objects;

/** 单用户单有效 Token；刷新原地替换摘要，撤销保留审计时间。 */
public final class OpenApiToken {

    private final Long id;
    private final Long version;
    private final long ownerId;
    private final OpenApiTokenHash tokenHash;
    private final Instant rotatedAt;
    private final Instant lastUsedAt;

    private OpenApiToken(Long id, Long version, long ownerId, OpenApiTokenHash tokenHash,
                         Instant rotatedAt, Instant lastUsedAt) {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("Token 必须关联真实用户");
        }
        this.id = id;
        this.version = version;
        this.ownerId = ownerId;
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.rotatedAt = Objects.requireNonNull(rotatedAt);
        this.lastUsedAt = lastUsedAt;
    }

    public static OpenApiToken issue(long ownerId, OpenApiTokenHash tokenHash, Instant now) {
        return new OpenApiToken(null, null, ownerId, tokenHash, now, null);
    }

    public static OpenApiToken rehydrate(Long id, Long version, long ownerId, OpenApiTokenHash tokenHash,
                                         Instant rotatedAt, Instant lastUsedAt) {
        return new OpenApiToken(id, version, ownerId, tokenHash, rotatedAt, lastUsedAt);
    }

    /** 刷新即轮换：替换摘要和轮换时间，旧摘要无法再认证。 */
    public OpenApiToken rotateTo(OpenApiTokenHash replacement, Instant now) {
        return new OpenApiToken(id, version, ownerId, replacement, now, lastUsedAt);
    }

    /** 认证命中时记录最近使用时间。 */
    public OpenApiToken markUsed(Instant now) {
        return new OpenApiToken(id, version, ownerId, tokenHash, rotatedAt, now);
    }

    public Long id() {
        return id;
    }

    public Long version() {
        return version;
    }

    public long ownerId() {
        return ownerId;
    }

    public OpenApiTokenHash tokenHash() {
        return tokenHash;
    }

    public Instant rotatedAt() {
        return rotatedAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }
}
