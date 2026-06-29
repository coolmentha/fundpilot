package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fund_transaction")
@SQLDelete(sql = "UPDATE fund_transaction SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundTransactionEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)  // 必须用 LAZY，绝不用 EAGER
    @JoinColumn(name = "fund_id", nullable = false) // 数据库外键列名
    private FundEntity fundEntity;

    private BigDecimal amount;

    private Instant confirmTime;

    private Instant cancelTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundTransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FundTransactionSource source;

    private BigDecimal shares;

    private BigDecimal nav;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_log_id")
    private SignalLogEntity signalLogEntity;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_fund_transaction_id")
    private FundTransactionEntity relatedFundTransactionEntity;
}
