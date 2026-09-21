package com.fundpilot.backend.alerting.infrastructure.persistence.alertrule;

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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 提醒规则持久化实体；独立声明审计列与软删，不继承 {@code AbstractEntity}。 */
@Entity
@Table(name = "alert_rule")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@SQLDelete(sql = "UPDATE alert_rule SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
class AlertRuleJpaEntity {

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

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "portfolio_fund_id")
    private Long portfolioFundId;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
