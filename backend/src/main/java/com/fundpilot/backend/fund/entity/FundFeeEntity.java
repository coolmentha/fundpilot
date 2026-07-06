package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 基金费率缓存:从天天基金 {@code jjfl_<code>.html} 爬取并落库。
 * <p>费率慢变(基金合同修改才改),每日 06:30 {@code FundFeeRefreshJob} 刷新 + 启动预热。
 * 供 {@code TransactionConfirmSupport} 买入扣申购费({@link #discountRate})、
 * 卖出按持有期查赎回费率阶梯({@link #redemptionLadder} JSON)。
 */
@Entity
@Table(name = "fund_fee")
@SQLDelete(sql = "UPDATE fund_fee SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundFeeEntity extends AbstractEntity {
    /** 基金代码(如 001071),与 {@code FundEntity.fundCode} 一致,唯一索引。 */
    private String fundCode;
    /** 原申购费率(小数,如 0.015 表 1.5%)。仅展示,实际扣费用 {@link #discountRate}。 */
    private BigDecimal purchaseRate;
    /** 优惠申购费率(小数,如 0.0015 表 0.15%,天天基金 1折)。买入扣费用此。 */
    private BigDecimal discountRate;
    /** 销售服务费率年化(小数,C类基金非0,A类通常0)。已在净值扣,不单独算,仅展示。 */
    private BigDecimal salesServiceFee;
    /** 赎回费率阶梯 JSON,格式 {@code [{"maxDays":7,"rate":0.015},...,{"maxDays":null,"rate":0}]}。 */
    private String redemptionLadder;
    /** 费率爬取时间(UTC)。 */
    private Instant fetchedAt;
}
