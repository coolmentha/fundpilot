package com.fundpilot.backend.identityaccess.infrastructure.persistence.opentoken;

import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiToken;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenHash;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class OpenApiTokenRepositoryImpl implements OpenApiTokenRepository {

    private final OpenApiTokenJpaRepository repository;
    private final Clock clock;

    @Override
    public Optional<OpenApiToken> findActiveByOwner(long ownerId) {
        return repository.findByOwnerId(ownerId).map(OpenApiTokenRepositoryImpl::toDomain);
    }

    @Override
    public Optional<OpenApiToken> findActiveByHash(OpenApiTokenHash tokenHash) {
        return repository.findByTokenHash(tokenHash.value()).map(OpenApiTokenRepositoryImpl::toDomain);
    }

    @Override
    public Optional<OpenApiToken> findActiveByOwnerForUpdate(long ownerId) {
        return repository.findActiveByOwnerForUpdate(ownerId).map(OpenApiTokenRepositoryImpl::toDomain);
    }

    @Override
    public OpenApiToken save(OpenApiToken token) {
        OpenApiTokenJpaEntity entity;
        if (token.id() == null) {
            entity = new OpenApiTokenJpaEntity();
        } else {
            entity = repository.findById(token.id()).orElseThrow();
        }
        entity.setOwnerId(token.ownerId());
        entity.setTokenHash(token.tokenHash().value());
        entity.setRotatedAt(token.rotatedAt());
        entity.setLastUsedAt(token.lastUsedAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public void markUsed(OpenApiTokenHash tokenHash, Instant now, Instant threshold) {
        repository.findByTokenHash(tokenHash.value())
                .filter(token -> token.getLastUsedAt() == null
                        || token.getLastUsedAt().isBefore(threshold))
                .ifPresent(token -> {
                    token.setLastUsedAt(now);
                    repository.save(token);
                });
    }

    @Override
    public void revokeActive(long ownerId) {
        Instant now = clock.instant();
        repository.findByOwnerId(ownerId).ifPresent(token -> {
            token.setRevokedAt(now);
            repository.save(token);
        });
    }

    private static OpenApiToken toDomain(OpenApiTokenJpaEntity entity) {
        return OpenApiToken.rehydrate(entity.getId(), entity.getVersion(), entity.getOwnerId(),
                new OpenApiTokenHash(entity.getTokenHash()), entity.getRotatedAt(), entity.getLastUsedAt());
    }
}
