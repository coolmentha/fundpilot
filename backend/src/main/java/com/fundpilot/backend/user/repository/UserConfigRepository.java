package com.fundpilot.backend.user.repository;

import com.fundpilot.backend.user.entity.UserConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConfigRepository extends JpaRepository<UserConfigEntity, Long> {
}
