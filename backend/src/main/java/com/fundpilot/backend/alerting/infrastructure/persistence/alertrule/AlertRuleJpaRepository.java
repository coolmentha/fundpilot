package com.fundpilot.backend.alerting.infrastructure.persistence.alertrule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AlertRuleJpaRepository extends JpaRepository<AlertRuleJpaEntity, Long> {

    List<AlertRuleJpaEntity> findByOwnerIdAndDeletedDateIsNullOrderByIdAsc(long ownerId);

    List<AlertRuleJpaEntity> findByEnabledTrue();
}
