package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 行情指标快照视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 * snapshotDate 统一用 Instant(经 InstantDateConverter 映射 SQL DATE)。
 *
 * @param id                    快照 ID
 * @param fundId                基金 ID
 * @param snapshotDate          快照日期(UTC 0 点)
 * @param currentNav            当前净值
 * @param priceAboveYearLine    净值是否在年线上方
 * @param yearLineRising        年线是否向上
 * @param weeklyMacdState       周 MACD 状态
 * @param volumeState           成交量状态
 * @param weeklyDropPercent     单周跌幅
 * @param sixtyDayHigh          是否 60 日新高
 * @param createdDate           创建时间
 */
public record MarketIndicatorSnapshotView(
        Long id,
        Long fundId,
        Instant snapshotDate,
        BigDecimal currentNav,
        boolean priceAboveYearLine,
        boolean yearLineRising,
        WeeklyMacdState weeklyMacdState,
        VolumeState volumeState,
        BigDecimal weeklyDropPercent,
        boolean sixtyDayHigh,
        Instant createdDate) {

    public static MarketIndicatorSnapshotView from(MarketIndicatorSnapshotEntity snapshot) {
        return new MarketIndicatorSnapshotView(
                snapshot.getId(),
                snapshot.getFundEntity() != null ? snapshot.getFundEntity().getId() : null,
                snapshot.getSnapshotDate(),
                snapshot.getCurrentNav(),
                snapshot.isPriceAboveYearLine(),
                snapshot.isYearLineRising(),
                snapshot.getWeeklyMacdState(),
                snapshot.getVolumeState(),
                snapshot.getWeeklyDropPercent(),
                snapshot.isSixtyDayHigh(),
                snapshot.getCreatedDate());
    }
}
