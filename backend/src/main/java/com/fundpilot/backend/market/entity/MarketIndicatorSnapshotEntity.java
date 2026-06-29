package com.fundpilot.backend.market.entity;

import com.fundpilot.backend.common.AbstractEntity;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 每日 14:50 行情指标快照(表级缓存)。每只基金每日一行,
 * {@code MarketDataFetchJob} 写入、{@code SignalGenerationJob} 读取,落库后不再发外部请求。
 * <p>字段对应信号引擎九步流程所需的全部行情输入:年线状态 / 周 MACD / 成交量 / 60 日新高 /
 * 最近累计净值 / 单周跌幅。详见 CONTEXT.md「行情数据缓存」「调节系数表」。
 */
@Entity
@Table(name = "market_indicator_snapshot")
@SQLDelete(sql = "UPDATE market_indicator_snapshot SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class MarketIndicatorSnapshotEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private FundEntity fundEntity;

    @Column(nullable = false)
    @Convert(converter = com.fundpilot.backend.common.InstantDateConverter.class)
    private Instant snapshotDate;

    private BigDecimal currentNav;

    private boolean priceAboveYearLine;

    private boolean yearLineRising;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private WeeklyMacdState weeklyMacdState;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private VolumeState volumeState;

    private BigDecimal weeklyDropPercent;

    // AC 指定列名 is_sixty_day_high(带 is_ 前缀);Java 字段按惯例用 sixtyDayHigh,Lombok 生成 isSixtyDayHigh()。
    @Column(name = "is_sixty_day_high")
    private boolean sixtyDayHigh;
}
