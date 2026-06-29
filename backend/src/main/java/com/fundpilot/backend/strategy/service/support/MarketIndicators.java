package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;

import java.math.BigDecimal;

/**
 * 行情指标快照(issue #12):信号引擎九步流程所需的全部行情输入,
 * 由 {@code MarketIndicatorSnapshotEntity} 映射而来(表级缓存,落库后不再发外部请求)。
 *
 * @param currentNav             最近累计净值
 * @param priceAboveYearLine     最近累计净值是否在 250 日年线上方(逻辑止损条件①)
 * @param yearLineRising         年线是否向上(建仓条件②)
 * @param weeklyMacdState        周 MACD 形态(调节系数 + 逻辑止损条件②"绿柱扩大")
 * @param volumeState            成交量形态(调节系数 + 破位观望)
 * @param weeklyDropPercent      单周跌幅([T-5,T-1] 两点跌幅,冷静判定 + 主动基金逻辑止损条件③)
 * @param sixtyDayHigh           今天是否为近 60 日累计净值最大值(建仓条件③"今天创新高")
 * @param benchmarkVolumeState   跟踪指数当日成交量形态(ETF/INDEX/INDEX_ENHANCED 逻辑止损条件③)
 * @param benchmarkDroppedToday  跟踪指数当日是否收跌(ETF/INDEX/INDEX_ENHANCED 逻辑止损条件③)
 */
public record MarketIndicators(
        BigDecimal currentNav,
        boolean priceAboveYearLine,
        boolean yearLineRising,
        WeeklyMacdState weeklyMacdState,
        VolumeState volumeState,
        BigDecimal weeklyDropPercent,
        boolean sixtyDayHigh,
        VolumeState benchmarkVolumeState,
        boolean benchmarkDroppedToday) {
}
