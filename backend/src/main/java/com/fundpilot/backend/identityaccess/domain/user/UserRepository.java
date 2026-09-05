package com.fundpilot.backend.identityaccess.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    List<User> findAll();

    Optional<User> findById(long id);

    Optional<User> findByUsername(String username);

    Optional<User> findFirstEnabledByRole(UserRole role);

    Optional<User> lockFirstEnabledByRole(UserRole role);

    long countEnabledByRole(UserRole role);

    User save(User user);
}
