package com.fundpilot.backend.identityaccess.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    List<User> findAll();

    Optional<User> findById(long id);

    Optional<User> findByUsername(String username);

    /** 按邮箱查账号（邮箱不区分大小写），用于自助设置提醒邮箱时的唯一性校验。 */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findFirstEnabledByRole(UserRole role);

    Optional<User> lockFirstEnabledByRole(UserRole role);

    long countEnabledByRole(UserRole role);

    User save(User user);
}
