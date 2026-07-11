package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 买入 lot(税lot):每笔确认的买入(INCREASE/TRANSFER_IN/INVEST)建一行。
 * <p>卖出时按 {@link #acquireDate} ASC FIFO 消耗 {@link #remainingShares},
 * 供赎回费按持有期(卖出交易发生日 − {@link #acquireDate})匹配阶梯。
 */
@Entity
@Table(name = "fund_lot")
@SQLDelete(sql = "UPDATE fund_lot SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundLotEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private FundEntity fundEntity;

    /** 买入交易 id({@code FundTransactionEntity.id}),逻辑外键,不加 FK 约束(跟随项目约定)。 */
    @Column(name = "acquire_tx_id", nullable = false)
    private Long acquireTxId;

    /** 买入交易发生时间(= 买入 tx 的 {@code tradeDate}),持有期锚点。 */
    @Column(name = "acquire_date", nullable = false)
    private Instant acquireDate;

    /** 买入份额(扣申购费后)。 */
    @Column(name = "acquire_shares", nullable = false)
    private BigDecimal acquireShares;

    /** 剩余未卖出份额(FIFO 消耗时递减)。 */
    @Column(name = "remaining_shares", nullable = false)
    private BigDecimal remainingShares;

    /** 买入成本单价(用户完整投入 amount / 到账 shares，含申购费)。 */
    @Column(name = "acquire_cost_per_share", nullable = false)
    private BigDecimal acquireCostPerShare;
}
