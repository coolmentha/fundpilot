package com.fundpilot.backend.accounting.domain.lot;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账目侧的费率快照。费率缺失时降级为不扣费，不阻断交易确认。
 *
 * @param subscriptionRate 申购费率（小数）
 * @param redemptionLadder 赎回费率阶梯，按持有天数上界升序
 */
public record FeeSchedule(BigDecimal subscriptionRate, List<RedemptionTier> redemptionLadder) {

    public FeeSchedule {
        subscriptionRate = subscriptionRate == null ? BigDecimal.ZERO : subscriptionRate;
        redemptionLadder = redemptionLadder == null ? List.of() : List.copyOf(redemptionLadder);
    }

    public static FeeSchedule none() {
        return new FeeSchedule(BigDecimal.ZERO, List.of());
    }

    /**
     * 按持有天数查赎回费率：首个 {@code maxDays == null}（最后一档）或 {@code holdingDays < maxDays}
     * 的档位即命中；阶梯为空时返回 0。
     */
    public BigDecimal redemptionRateFor(long holdingDays) {
        for (RedemptionTier tier : redemptionLadder) {
            if (tier.maxDays() == null || holdingDays < tier.maxDays()) {
                return tier.rate();
            }
        }
        return BigDecimal.ZERO;
    }

    /** 赎回费率阶梯档位。{@code maxDays} 为空表示最后一档，覆盖所有更长持有期。 */
    public record RedemptionTier(Integer maxDays, BigDecimal rate) {
        public RedemptionTier {
            rate = rate == null ? BigDecimal.ZERO : rate;
        }
    }
}
