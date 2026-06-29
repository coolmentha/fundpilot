package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测模拟结果(issue #11)。
 *
 * @param strategyReturn 策略收益率 =(期末市值 - 累计投入)/ 累计投入
 * @param dailyValues    逐日市值序列(用于算最大回撤)
 */
public record BacktestResult(BigDecimal strategyReturn, List<BigDecimal> dailyValues) {
}
