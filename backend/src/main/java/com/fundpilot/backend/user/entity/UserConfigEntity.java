package com.fundpilot.backend.user.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/**
 * 单用户账户配置(单用户场景,不存 userId)。行情工作台转向后,只剩 {@code watchedIndices}——
 * 用户关注的大盘指数列表(secid 逗号分隔),供行情缓存层按需拉取。
 * <p>历史:曾存 {@code totalInvestableCapital}(总可投资金,总仓位≤80%硬约束的分母),
 * 平台转向行情后仓位管理移除,V9 迁移删除该列。
 */
@Entity
@Table(name = "user_config")
@SQLDelete(sql = "UPDATE user_config SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class UserConfigEntity extends AbstractEntity {

    /** 用户关注的大盘指数列表(secid 逗号分隔,如 "1.000001,1.000300,0.399006");null/空用默认列表。 */
    @Column(name = "watched_indices", length = 512)
    private String watchedIndices;
}
