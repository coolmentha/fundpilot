package com.fundpilot.backend.market.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A 股交易日历,记录每个日期是否为交易日(含节假日剔除)。
 * {@code MIN_HOLD_DAYS} 判定 5 个交易日窗口时遍历本表跳过非交易日,见 CONTEXT.md「交易日历」。
 * 本期人工维护,不做自动同步。{@code calendarDate} 全局唯一。
 */
@Entity
@Table(name = "trading_calendar")
@Getter
@Setter
public class TradingCalendarEntity extends AbstractEntity {

    @Column(nullable = false)
    private LocalDate calendarDate;

    // AC 指定列名 is_trading_day(带 is_ 前缀);Java 字段按惯例用 tradingDay,Lombok 生成 isTradingDay()。
    @Column(name = "is_trading_day", nullable = false)
    private boolean tradingDay;
}
