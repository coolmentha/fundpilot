package com.fundpilot.backend.discipline.infrastructure.persistence.advice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** V41 回填建议的持久化模型；跨上下文关联只保存 ID。 */
@Entity
@Table(name = "discipline_advice")
@SQLRestriction("deleted_date IS NULL")
@Getter
@Setter
class DisciplineAdviceJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "portfolio_fund_id")
    private Long portfolioFundId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "discipline_strategy_id")
    private Long disciplineStrategyId;

    @Column(name = "signal_date")
    private Instant signalDate;

    @Column(name = "signal_type")
    private Short signalType;
    @Column(name = "trigger_tier") private Integer triggerTier;
    private BigDecimal coefficient;
    @Column(name = "suggested_value") private BigDecimal suggestedValue;
    @Column(name = "suggested_measure_unit") private String suggestedMeasureUnit;
    private String reason;
    private String warnings;
    @Column(name = "hard_constraint_breaches") private String hardConstraintBreaches;

    @Column(name = "ignored_date")
    private Instant ignoredDate;

    @Column(name = "response_status")
    private String responseStatus;
}
