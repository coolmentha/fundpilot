package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/**
 * 基金字典(东方财富 {@code fundcode_search.js} 全量约 2 万条的本地缓存,ADR-0005)。
 * <p>搜索框自动补全查这张表,避免每次按键现拉外部接口撞限流。落库时同步跑
 * {@code fundSubType} + {@code fundCategory} 识别并缓存识别结果,搜索返回的候选自带分类。
 * <p>写入只发生在 {@code FundDictSyncService} 同步任务,读多写少。
 */
@Entity
@Table(name = "fund_dict")
@SQLDelete(sql = "UPDATE fund_dict SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundDictEntity extends AbstractEntity {

    /** 基金代码(如 510300),字典唯一键。 */
    @Column(name = "fund_code", nullable = false, length = 16)
    private String fundCode;

    /** 基金名称(如 易方达沪深300ETF联接A)。 */
    @Column(name = "fund_name", nullable = false, length = 128)
    private String fundName;

    /** 东方财富原始类型描述(如 混合型-灵活/指数型-股票/债券型-混合二级),仅存档,不参与 fundCategory 判定。 */
    @Column(name = "raw_name", length = 64)
    private String rawName;

    /** 数据源维度分类(ETF/INDEX/INDEX_ENHANCED/ACTIVE),落库时由 FundSubTypeClassifier 识别填入。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "fund_sub_type", length = 32)
    private FundSubType fundSubType;

    /** 策略参数维度分类(宽基/行业/主动/混合),落库时由 FundCategoryClassifier 识别填入。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "fund_category", length = 32)
    private FundCategory fundCategory;

    /** 跟踪/基准指数代码(如 000300.SH),落库时识别填入。 */
    @Column(name = "benchmark_index_code", length = 64)
    private String benchmarkIndexCode;
}
