package com.fundpilot.backend.marketdata.domain.indexvaluation;

import java.math.BigDecimal;
import java.time.Instant;

public record IndexValuation(String indexCode, Instant tradeDate, BigDecimal peRatio, String source) {
    public IndexValuation {
        if (indexCode == null || indexCode.isBlank()) throw new IllegalArgumentException("指数代码不能为空");
        if (tradeDate == null) throw new IllegalArgumentException("估值日期不能为空");
        if (peRatio == null || peRatio.signum() <= 0) throw new IllegalArgumentException("PE 必须大于 0");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("估值来源不能为空");
        indexCode = indexCode.trim();
        source = source.trim();
    }
}
