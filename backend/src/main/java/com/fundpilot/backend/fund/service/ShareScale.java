package com.fundpilot.backend.fund.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 基金份额统一保留两位，最小单位 0.01 份。 */
public final class ShareScale {

    public static final int SCALE = 2;

    private ShareScale() {
    }

    public static BigDecimal normalize(BigDecimal shares) {
        return shares == null ? null : shares.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
