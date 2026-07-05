package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;

/**
 * 卖出消耗 lot 记录:每笔卖出按 FIFO 拆成多行(每行对应一个被消耗的 {@link FundLotEntity})。
 * <p>记录 {@link #holdingDays}(卖出确认日 − lot.acquireDate 自然日)和 {@link #redemptionRate},
 * 供校验与前端展示赎回费明细。
 */
@Entity
@Table(name = "fund_lot_redemption")
@SQLDelete(sql = "UPDATE fund_lot_redemption SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundLotRedemptionEntity extends AbstractEntity {
    /** 被消耗的 lot id({@code FundLotEntity.id}),逻辑外键。 */
    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    /** 卖出交易 id({@code FundTransactionEntity.id}),逻辑外键。 */
    @Column(name = "sell_tx_id", nullable = false)
    private Long sellTxId;

    /** 本次消耗的份额。 */
    @Column(name = "shares_consumed", nullable = false)
    private BigDecimal sharesConsumed;

    /** 持有天数(卖出确认日 − lot.acquireDate 的自然日)。 */
    @Column(name = "holding_days", nullable = false)
    private Integer holdingDays;

    /** 赎回费率(小数,如 0.005 表 0.5%)。费率缺失降级时为 0。 */
    @Column(name = "redemption_rate", nullable = false)
    private BigDecimal redemptionRate;
}
