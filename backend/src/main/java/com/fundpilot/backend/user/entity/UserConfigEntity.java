package com.fundpilot.backend.user.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import java.math.BigDecimal;

/**
 * 用户级月度定投预算配置。
 */
@Entity
@Table(name = "user_config")
@SQLDelete(sql = "UPDATE user_config SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class UserConfigEntity extends AbstractEntity {
    @Column(name = "owner_id")
    private Long ownerId;

    /** 每月定投预算；null 表示仅展示定投金额而不比较预算。 */
    @Column(name = "monthly_dca_budget")
    private BigDecimal monthlyDcaBudget;

}
