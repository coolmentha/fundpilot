package com.fundpilot.backend.alerting.infrastructure.persistence.suggestion;

import com.fundpilot.backend.alerting.domain.suggestion.SuggestionState;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitPhase;

final class SuggestionStatePersistenceMapper {

    private SuggestionStatePersistenceMapper() {
    }

    static SuggestionState toDomain(SuggestionStateJpaEntity entity) {
        return SuggestionState.rehydrate(entity.getId(), entity.getAlertRuleId(), entity.getOwnerId(),
                entity.getPortfolioFundId(), TakeProfitPhase.valueOf(entity.getPhase()), entity.getCycleStartedAt(),
                entity.getCyclePeakNav(), entity.getCooldownStartedAt());
    }

    /** 把领域状态写入目标实体；新建传空实体，更新传已加载实体以保留乐观锁版本。 */
    static SuggestionStateJpaEntity apply(SuggestionState state, SuggestionStateJpaEntity entity) {
        entity.setAlertRuleId(state.alertRuleId());
        entity.setOwnerId(state.ownerId());
        entity.setPortfolioFundId(state.portfolioFundId());
        entity.setPhase(state.phase().name());
        entity.setCycleStartedAt(state.cycleStartedAt());
        entity.setCyclePeakNav(state.cyclePeakNav());
        entity.setCooldownStartedAt(state.cooldownStartedAt());
        return entity;
    }
}