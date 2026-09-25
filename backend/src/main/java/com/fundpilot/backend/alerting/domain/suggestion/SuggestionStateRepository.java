package com.fundpilot.backend.alerting.domain.suggestion;

import java.util.List;
import java.util.Optional;

/** 建议型规则运行期状态的持久化端口。 */
public interface SuggestionStateRepository {

    Optional<SuggestionState> findByRuleAndFund(long alertRuleId, long portfolioFundId);

    List<SuggestionState> findByRule(long alertRuleId);

    SuggestionState save(SuggestionState state);

    /** 规则被编辑或删除后状态不再有效，直接物理清除。 */
    void deleteByRule(long alertRuleId);
}