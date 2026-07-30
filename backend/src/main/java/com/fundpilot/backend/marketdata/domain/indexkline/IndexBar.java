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
}
