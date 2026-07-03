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
 * 单用户账户配置(本期单用户场景,不存 userId)。存 {@code totalInvestableCapital}——
 * 用户整个账户的总可投资金(含未入场现金、其他基金等),是总仓位 ≤ 80% 硬约束的分母,
 * 见 CONTEXT.md「总可投资金」。用户首次配置时填,非 Σ plannedTotalAmount 反推。
 */
@Entity
@Table(name = "user_config")
@SQLDelete(sql = "UPDATE user_config SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class UserConfigEntity extends AbstractEntity {

    @Column(nullable = false)
    private BigDecimal totalInvestableCapital;

    /** 用户关注的大盘指数列表(secid 逗号分隔,如 "1.000001,1.000300,0.399006");null/空用默认列表。 */
    @Column(name = "watched_indices", length = 512)
    private String watchedIndices;
}
