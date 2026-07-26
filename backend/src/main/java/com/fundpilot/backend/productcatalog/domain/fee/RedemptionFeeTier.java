package com.fundpilot.backend.productcatalog.domain.fee;

import java.math.BigDecimal;
import java.util.Objects;

public record RedemptionFeeTier(Integer maxDays, BigDecimal rate) {
    public RedemptionFeeTier {
        if (maxDays != null && maxDays <= 0) throw new IllegalArgumentException("持有天数上限必须为正数");
        Objects.requireNonNull(rate, "赎回费率不能为空");
        if (rate.signum() < 0) throw new IllegalArgumentException("赎回费率不能为负数");
    }
}
