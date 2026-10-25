package com.fundpilot.backend.identityaccess.domain.opentoken;

import java.time.Instant;
import java.util.Optional;

public interface OpenApiTokenRepository {

    Optional<OpenApiToken> findActiveByOwner(long ownerId);

    Optional<OpenApiToken> findActiveByHash(OpenApiTokenHash tokenHash);

    /** 创建/轮换需要串行化同一用户的并发请求。 */
    Optional<OpenApiToken> findActiveByOwnerForUpdate(long ownerId);

    OpenApiToken save(OpenApiToken token);

    /** 仅在超过节流窗口后更新最近使用时间，避免高频只读请求反复写库。 */
    void markUsed(OpenApiTokenHash tokenHash, Instant now, Instant threshold);

    /** 软撤销有效 Token，保留摘要审计；不物理删除，避免历史摘要被重新使用。 */
    void revokeActive(long ownerId);
}
