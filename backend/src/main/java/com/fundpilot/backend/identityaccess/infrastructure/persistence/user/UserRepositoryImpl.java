package com.fundpilot.backend.identityaccess.infrastructure.persistence.user;

import com.fundpilot.backend.identityaccess.domain.user.User;
import com.fundpilot.backend.identityaccess.domain.user.UserRepository;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserRepositoryImpl implements UserRepository {

    private final SiteUserJpaRepository repository;

    @Override
    public List<User> findAll() {
        return repository.findAll().stream().map(SiteUserPersistenceMapper::toDomain).toList();
    }

    @Override
    public Optional<User> findById(long id) {
        return repository.findById(id).map(SiteUserPersistenceMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username).map(SiteUserPersistenceMapper::toDomain);
    }

    @Override
    public Optional<User> findFirstEnabledByRole(UserRole role) {
        return repository.findFirstByRoleAndEnabledTrueOrderByIdAsc(role)
                .map(SiteUserPersistenceMapper::toDomain);
    }

    @Override
    public long countEnabledByRole(UserRole role) {
        return repository.countByRoleAndEnabledTrue(role);
    }

    @Override
    public User save(User user) {
        SiteUserJpaEntity entity;
        if (user.id() == null) {
            entity = SiteUserPersistenceMapper.toEntity(user);
        } else {
            entity = repository.findById(user.id()).orElseThrow();
            SiteUserPersistenceMapper.copyMutable(user, entity);
        }
        return SiteUserPersistenceMapper.toDomain(repository.save(entity));
    }
}
