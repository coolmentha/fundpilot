package com.fundpilot.backend.alerting.infrastructure.persistence.notification;

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
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 提醒发送记录持久化实体；仅追加，不物理删除。 */
@Entity
@Table(name = "alert_notification")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@Getter
@Setter
class AlertNotificationJpaEntity {

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

    @Column(name = "alert_rule_id", nullable = false)
    private Long alertRuleId;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @Column(name = "trading_date", nullable = false)
    private Instant tradingDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "fund_count", nullable = false)
    private int fundCount;

    @Column(name = "trigger_summary", nullable = false)
    private String triggerSummary;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;
}
