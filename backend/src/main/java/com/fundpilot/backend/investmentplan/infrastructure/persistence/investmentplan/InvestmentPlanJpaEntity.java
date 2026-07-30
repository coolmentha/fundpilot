package com.fundpilot.backend.investmentplan.infrastructure.persistence.investmentplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "investment_plan")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@SQLDelete(sql = "UPDATE investment_plan SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
class InvestmentPlanJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @CreatedDate @Column(updatable = false) private Instant createdDate;
    @LastModifiedDate private Instant updatedDate;
    @Column(name = "legacy_dca_plan_id") private Long legacyDcaPlanId;
    @Column(name = "portfolio_fund_id") private Long portfolioFundId;
    @Column(name = "owner_id") private Long ownerId;
    private boolean enabled;
    private BigDecimal amount;
    private String frequency;
    @Column(name = "day_of_week") private Integer dayOfWeek;
    @Column(name = "day_of_month") private Integer dayOfMonth;
    private String status;
}
