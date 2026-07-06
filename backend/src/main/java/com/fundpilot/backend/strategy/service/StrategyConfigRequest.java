package com.fundpilot.backend.strategy.service;

import java.math.BigDecimal;

/**
 * 策略参数配置请求:金字塔退场后只剩移动止盈回落幅度。
 *
 * @param stopLossPullbackPercent  移动止盈回落幅度(回落 n×本阈值触发卖 holdingShares×n/4)
 */
public record StrategyConfigRequest(
        BigDecimal stopLossPullbackPercent) {
}
