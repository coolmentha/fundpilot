package com.fundpilot.backend.marketdata.domain.publishednav;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PublishedNav(Long id, Long legacyFundId, long fundProductId, String fundCode,
                           Instant navDate, BigDecimal unitNav, BigDecimal accumulatedNav,
                           Instant firstSeenAt) {
    public PublishedNav {
        if (fundProductId <= 0) throw new IllegalArgumentException("基金产品标识必须为正数");
        if (fundCode == null || fundCode.isBlank()) throw new IllegalArgumentException("基金代码不能为空");
        Objects.requireNonNull(navDate, "净值日期不能为空");
        Objects.requireNonNull(unitNav, "单位净值不能为空");
        Objects.requireNonNull(firstSeenAt, "首次发现时间不能为空");
        if (unitNav.signum() <= 0) throw new IllegalArgumentException("单位净值必须大于零");
        if (accumulatedNav != null && accumulatedNav.signum() <= 0) {
            throw new IllegalArgumentException("累计净值必须大于零");
        }
        fundCode = fundCode.trim();
    }

    public static PublishedNav publish(Long legacyFundId, long fundProductId, String fundCode,
                                       Instant navDate, BigDecimal unitNav,
                                       BigDecimal accumulatedNav, Instant firstSeenAt) {
        return new PublishedNav(null, legacyFundId, fundProductId, fundCode, navDate,
                unitNav, accumulatedNav, firstSeenAt);
    }
}
