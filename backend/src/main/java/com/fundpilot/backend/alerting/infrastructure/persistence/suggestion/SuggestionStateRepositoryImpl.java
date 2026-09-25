package com.fundpilot.backend.alerting.infrastructure.persistence.suggestion;

import com.fundpilot.backend.alerting.domain.suggestion.SuggestionState;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionStateRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class SuggestionStateRepositoryImpl implements SuggestionStateRepository {

    private final SuggestionStateJpaRepository states;

    @Override
    public Optional<SuggestionState> findByRuleAndFund(long alertRuleId, long portfolioFundId) {
        return states.findByAlertRuleIdAndPortfolioFundId(alertRuleId, portfolioFundId)
                .map(SuggestionStatePersistenceMapper::toDomain);
    }

    @Override
    public List<SuggestionState> findByRule(long alertRuleId) {
        return states.findByAlertRuleId(alertRuleId).stream().map(SuggestionStatePersistenceMapper::toDomain).toList();
    }

    @Override
    public SuggestionState save(SuggestionState state) {
        SuggestionStateJpaEntity target = state.id() == null
                ? new SuggestionStateJpaEntity()
                : states.findById(state.id()).orElseThrow();
        return SuggestionStatePersistenceMapper.toDomain(
                states.save(SuggestionStatePersistenceMapper.apply(state, target)));
    }

    @Override
    @Transactional
    public void deleteByRule(long alertRuleId) {
        states.deleteByAlertRuleId(alertRuleId);
    }
}