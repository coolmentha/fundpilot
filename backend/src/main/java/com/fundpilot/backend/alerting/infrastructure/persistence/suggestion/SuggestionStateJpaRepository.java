package com.fundpilot.backend.alerting.infrastructure.persistence.suggestion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SuggestionStateJpaRepository extends JpaRepository<SuggestionStateJpaEntity, Long> {

    Optional<SuggestionStateJpaEntity> findByAlertRuleIdAndPortfolioFundId(long alertRuleId, long portfolioFundId);

    List<SuggestionStateJpaEntity> findByAlertRuleId(long alertRuleId);

    /** 规则被编辑或删除后状态不再有效，物理清除而不留软删行。 */
    @Modifying
    @Query("delete from SuggestionStateJpaEntity state where state.alertRuleId = :alertRuleId")
    void deleteByAlertRuleId(@Param("alertRuleId") long alertRuleId);
}