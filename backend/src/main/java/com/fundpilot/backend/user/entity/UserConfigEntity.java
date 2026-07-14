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
 * 单用户账户配置(单用户场景,不存 userId)。保存关注指数和可选月度定投预算。
 */
@Entity
@Table(name = "user_config")
@SQLDelete(sql = "UPDATE user_config SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class UserConfigEntity extends AbstractEntity {

    /** 每月定投预算；null 表示仅展示定投金额而不比较预算。 */
    @Column(name = "monthly_dca_budget")
    private BigDecimal monthlyDcaBudget;

    /** 用户关注的大盘指数列表(secid 逗号分隔,如 "1.000001,1.000300,0.399006");null/空用默认列表。 */
    @Column(name = "watched_indices", length = 512)
    private String watchedIndices;
}
