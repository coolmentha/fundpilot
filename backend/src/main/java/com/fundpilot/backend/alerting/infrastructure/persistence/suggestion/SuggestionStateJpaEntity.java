package com.fundpilot.backend.alerting.infrastructure.persistence.suggestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 建议型规则运行期状态的持久化实体；状态由规则派生，规则被编辑或删除时直接物理清除。 */
@Entity
@Table(name = "alert_suggestion_state")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
class SuggestionStateJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private Instant updatedDate;

    @Column(name = "deleted_date", insertable = false, updatable = false)
    private Instant deletedDate;

    @Column(name = "alert_rule_id", nullable = false)
    private Long alertRuleId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "portfolio_fund_id", nullable = false)
    private Long portfolioFundId;

    @Column(name = "phase", nullable = false)
    private String phase;

    @Column(name = "cycle_started_at")
    private Instant cycleStartedAt;

    @Column(name = "cycle_peak_nav")
    private BigDecimal cyclePeakNav;

    @Column(name = "cooldown_started_at")
    private Instant cooldownStartedAt;
}