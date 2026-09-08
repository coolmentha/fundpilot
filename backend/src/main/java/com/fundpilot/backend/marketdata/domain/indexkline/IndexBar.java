package com.fundpilot.backend.marketdata.domain.indexkline;

import java.math.BigDecimal;
import java.time.Instant;

public record IndexBar(String indexCode, Instant tradeDate, BigDecimal open,
                       BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {
    public IndexBar {
        if (indexCode == null || indexCode.isBlank()) throw new IllegalArgumentException("指数代码不能为空");
        if (tradeDate == null) throw new IllegalArgumentException("交易日期不能为空");
        indexCode = indexCode.trim();
    }

    /** 可用于日 K 图的完整 OHLCV；成交量允许为零。 */
    public boolean isComplete() {
        return open != null && high != null && low != null && close != null && volume != null
                && open.signum() > 0 && high.signum() > 0 && low.signum() > 0 && close.signum() > 0
                && volume >= 0 && high.compareTo(open.max(close)) >= 0
                && low.compareTo(open.min(close)) <= 0;
    }
}
