package com.fundpilot.backend.accounting.infrastructure.persistence.transaction;

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

/**
 * 账目流水持久化实体。Accounting 拥有 {@code fund_transaction} 表。
 *
 * <p>扩展期同时写 {@code portfolio_fund_id}（新所有权）与 legacy {@code fund_id}（回滚与对账），
 * 后者由 {@code LedgerTransactionRepositoryImpl} 从 {@code portfolio_fund} 解析，不进入领域模型。
 * 关联列一律映射为普通 ID，不建立跨模块 JPA 关联。
 */
@Entity
@Table(name = "fund_transaction")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@SQLDelete(sql = "UPDATE fund_transaction SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
class LedgerTransactionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdDate;

    @LastModifiedDate
    private Instant updatedDate;

    @Column(name = "deleted_date", insertable = false, updatable = false)
    private Instant deletedDate;

    @Column(name = "portfolio_fund_id")
    private Long portfolioFundId;

    /** legacy 所有权列，扩展期保持写入以支持回滚与对账。 */
    @Column(name = "fund_id", nullable = false)
    private Long legacyFundId;

    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "status", length = 32)
    private String status;

    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal shares;

    private BigDecimal nav;

    private BigDecimal fee;

    private BigDecimal feeRate;

    private Instant tradeDate;

    private Instant confirmTime;

    private Instant cancelTime;

    @Column(name = "related_fund_transaction_id")
    private Long relatedTransactionId;

    @Column(name = "signal_log_id")
    private Long signalLogId;

    private Long dcaPlanId;

    @Column(name = "discipline_advice_id")
    private Long disciplineAdviceId;

    @Column(name = "investment_plan_id")
    private Long investmentPlanId;
}
