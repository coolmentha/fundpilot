package com.fundpilot.backend.market.client;

import java.util.List;

/**
 * 赎回费档位快照(issue #60):某只基金的赎回费档位表 + 是否数据缺失。
 * <p>数据缺失时 {@code tiers} 为空、{@code missing}=true,调用方降级为 warnings 提示,不阻断赎回。
 *
 * @param fundCode 基金代码
 * @param tiers    赎回费档位(按持有期升序);空表示数据缺失
 * @param missing  数据是否缺失(拉取失败或解析无果)
 */
public record RedemptionFeeSnapshot(String fundCode, List<RedemptionFeeTier> tiers, boolean missing) {

    /** 数据缺失的空快照。 */
    public static RedemptionFeeSnapshot missing(String fundCode) {
        return new RedemptionFeeSnapshot(fundCode, List.of(), true);
    }
}