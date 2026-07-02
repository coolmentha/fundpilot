package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;

/**
 * 回测参数(ADR-0015 重写):从 {@code FundStrategyEntity} + {@code FundEntity} 派生。
 *
 * @param dcaAmount        每期定投金额(基金级用户输入,每月最后交易日扣款)
 * @param takeProfitParams 移动止盈参数(按 fundCategory 分派)
 * @param fundCategory     基金类型:行业止盈后暂停定投、收益跌回 10% 以下恢复;宽基一直定投不停
 */
public record BacktestParams(BigDecimal dcaAmount, TakeProfitParams takeProfitParams, FundCategory fundCategory) {
}