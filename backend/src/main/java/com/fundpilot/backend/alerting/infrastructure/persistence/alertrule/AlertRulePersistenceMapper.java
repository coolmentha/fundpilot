package com.fundpilot.backend.alerting.infrastructure.persistence.alertrule;

import com.fundpilot.backend.alerting.application.condition.AlertConditionJsonCodec;
import com.fundpilot.backend.alerting.application.suggestion.TakeProfitParamsJsonCodec;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;

final class AlertRulePersistenceMapper {

    private AlertRulePersistenceMapper() {
    }

    static AlertRule toDomain(AlertRuleJpaEntity entity) {
        return AlertRule.rehydrate(entity.getId(), entity.getVersion(), entity.getOwnerId(),
                AlertRuleScope.valueOf(entity.getScope()), entity.getPortfolioFundId(),
                AlertRuleKind.valueOf(entity.getKind()), AlertConditionJsonCodec.readOrNull(entity.getConditions()),
                TakeProfitParamsJsonCodec.read(entity.getParameters()), entity.isEnabled());
    }

    /** 把领域状态写入目标实体；新建传空实体，更新传已加载实体以保留乐观锁版本。 */
    static AlertRuleJpaEntity apply(AlertRule rule, AlertRuleJpaEntity entity) {
        entity.setOwnerId(rule.ownerId());
        entity.setScope(rule.scope().name());
        entity.setPortfolioFundId(rule.portfolioFundId());
        entity.setKind(rule.kind().name());
        entity.setConditions(rule.conditions() == null ? null : AlertConditionJsonCodec.write(rule.conditions()));
        entity.setParameters(TakeProfitParamsJsonCodec.write(rule.takeProfit()));
        entity.setEnabled(rule.enabled());
        return entity;
    }
}