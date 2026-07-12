package com.fundpilot.backend.user.repository;

import com.fundpilot.backend.user.entity.UserConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserConfigRepository extends JpaRepository<UserConfigEntity, Long> {

    /** 串行化单用户配置首次创建，避免“都读到空后各插一行”。 */
    @Query(value = "select pg_advisory_xact_lock(67511001)", nativeQuery = true)
    void lockSingleton();
}
