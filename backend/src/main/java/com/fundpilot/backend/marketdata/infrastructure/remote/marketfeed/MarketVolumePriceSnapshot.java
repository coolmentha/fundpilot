package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import java.math.BigDecimal;
import java.time.Instant;

/** 上证指数实时量价快照，涨跌幅为小数比例，量比以 1 为常态基准。 */
public record MarketVolumePriceSnapshot(
        BigDecimal changePct,
        BigDecimal volumeRatio,
        Instant quoteTime) {
}
