package com.fundpilot.backend.alerting.infrastructure.persistence.alertrule;

import com.fundpilot.backend.alerting.application.condition.AlertConditionJsonCodec;
import com.fundpilot.backend.alerting.application.grid.GridParamsJsonCodec;
import com.fundpilot.backend.alerting.application.suggestion.TakeProfitParamsJsonCodec;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.grid.GridParams;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;

/**
 * 规则领域模型与持久化实体的互转。
 *
 * <p>{@code parameters} 列被两种无条件的建议型规则共用：回撤止盈存六个止盈参数，网格存两个比例。
 * 读取时必须按 {@code kind} 分派，不能一律按止盈参数解析。
 */
final class AlertRulePersistenceMapper {

    private AlertRulePersistenceMapper() {
    }

    static AlertRule toDomain(AlertRuleJpaEntity entity) {
        AlertRuleKind kind = AlertRuleKind.valueOf(entity.getKind());
        return AlertRule.rehydrate(entity.getId(), entity.getVersion(), entity.getOwnerId(),
                AlertRuleScope.valueOf(entity.getScope()), entity.getPortfolioFundId(), kind,
                AlertConditionJsonCodec.readOrNull(entity.getConditions()),
                kind == AlertRuleKind.TRAILING_STOP ? TakeProfitParamsJsonCodec.read(entity.getParameters()) : null,
                kind == AlertRuleKind.GRID ? GridParamsJsonCodec.read(entity.getParameters()) : null,
                entity.isEnabled());
    }

    /** 把领域状态写入目标实体；新建传空实体，更新传已加载实体以保留乐观锁版本。 */
    static AlertRuleJpaEntity apply(AlertRule rule, AlertRuleJpaEntity entity) {
        entity.setOwnerId(rule.ownerId());
        entity.setScope(rule.scope().name());
        entity.setPortfolioFundId(rule.portfolioFundId());
        entity.setKind(rule.kind().name());
        entity.setConditions(rule.conditions() == null ? null : AlertConditionJsonCodec.write(rule.conditions()));
        entity.setParameters(parameters(rule));
        entity.setEnabled(rule.enabled());
        return entity;
    }

    /** 按种类选择参数列的内容；条件型规则与逻辑破坏止损没有参数。 */
    private static String parameters(AlertRule rule) {
        if (rule.kind() == AlertRuleKind.GRID) {
            GridParams grid = rule.grid();
            return GridParamsJsonCodec.write(grid);
        }
        if (rule.kind() == AlertRuleKind.TRAILING_STOP) {
            TakeProfitParams takeProfit = rule.takeProfit();
            return TakeProfitParamsJsonCodec.write(takeProfit);
        }
        return null;
    }
}
