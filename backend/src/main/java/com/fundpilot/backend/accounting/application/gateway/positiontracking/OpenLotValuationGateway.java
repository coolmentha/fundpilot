package com.fundpilot.backend.accounting.application.gateway.positiontracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Open lot 查询所需的最新净值与原始赎回费率事实；缺失不会降级为零。 */
public interface OpenLotValuationGateway {

    Optional<LatestNav> latestNav(long fundProductId);

    Optional<RedemptionSchedule> redemptionSchedule(long fundProductId);

    record LatestNav(Instant navDate, BigDecimal unitNav) {}

    record RedemptionSchedule(List<RedemptionTier> tiers) {
        public RedemptionSchedule {
            tiers = tiers == null ? List.of() : List.copyOf(tiers);
        }

        public Optional<BigDecimal> rateFor(long holdingDays) {
            return tiers.stream()
                    .filter(tier -> tier.maxDays() == null || holdingDays < tier.maxDays())
                    .findFirst()
                    .map(RedemptionTier::rate);
        }
    }

    record RedemptionTier(Integer maxDays, BigDecimal rate) {}
}
