package com.fundpilot.backend.alerting.infrastructure.persistence.alertrule;

import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AlertRuleRepositoryImpl implements AlertRuleRepository {

    private final AlertRuleJpaRepository rules;

    @Override
    public Optional<AlertRule> findById(long id) {
        return rules.findById(id).map(AlertRulePersistenceMapper::toDomain);
    }

    @Override
    public List<AlertRule> findByOwnerId(long ownerId) {
        return rules.findByOwnerIdAndDeletedDateIsNullOrderByIdAsc(ownerId).stream()
                .map(AlertRulePersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<AlertRule> findAllEnabled() {
        return rules.findByEnabledTrue().stream().map(AlertRulePersistenceMapper::toDomain).toList();
    }

    @Override
    public AlertRule save(AlertRule rule) {
        AlertRuleJpaEntity target = rule.id() == null
                ? new AlertRuleJpaEntity()
                : rules.findById(rule.id()).orElseThrow();
        return AlertRulePersistenceMapper.toDomain(rules.save(AlertRulePersistenceMapper.apply(rule, target)));
    }

    @Override
    public void softDelete(long id) {
        rules.deleteById(id);
    }
}
