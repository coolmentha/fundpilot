package com.fundpilot.backend.alerting.infrastructure.persistence.notification;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AlertNotificationJpaRepository extends JpaRepository<AlertNotificationJpaEntity, Long> {

    List<AlertNotificationJpaEntity> findByOwnerIdOrderByIdDesc(long ownerId, Pageable pageable);
}
