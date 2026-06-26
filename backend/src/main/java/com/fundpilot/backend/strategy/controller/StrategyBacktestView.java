package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 回测结果视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 *
 * @param id                              回测 ID
 * @param fundStrategyId                  策略 ID
 * @param backtestStartDate               回测起始日期
 * @param backtestEndDate                 回测结束日期
 * @param strategyReturn                  策略收益
 * @param strategyMaxDrawdown             策略最大回撤
 * @param benchmarkHs300Return            沪深300 基准收益
 * @param benchmarkAllInReturn            一次性投入基准收益
 * @param benchmarkDcaReturn              定投基准收益
 * @param benchmarkHs300MaxDrawdown       沪深300 基准最大回撤
 * @param benchmarkAllInMaxDrawdown       一次性投入基准最大回撤
 * @param benchmarkDcaMaxDrawdown         定投基准最大回撤
 * @param passed                          是否通过
 * @param createdDate                     创建时间
 */
public record StrategyBacktestView(
        Long id,
        Long fundStrategyId,
        Instant backtestStartDate,
        Instant backtestEndDate,
        BigDecimal strategyReturn,
        BigDecimal strategyMaxDrawdown,
        BigDecimal benchmarkHs300Return,
        BigDecimal benchmarkAllInReturn,
        BigDecimal benchmarkDcaReturn,
        BigDecimal benchmarkHs300MaxDrawdown,
        BigDecimal benchmarkAllInMaxDrawdown,
        BigDecimal benchmarkDcaMaxDrawdown,
        boolean passed,
        Instant createdDate) {

    public static StrategyBacktestView from(StrategyBacktestEntity backtest) {
        return new StrategyBacktestView(
                backtest.getId(),
                backtest.getFundStrategyEntity() != null ? backtest.getFundStrategyEntity().getId() : null,
                backtest.getBacktestStartDate(),
                backtest.getBacktestEndDate(),
                backtest.getStrategyReturn(),
                backtest.getStrategyMaxDrawdown(),
                backtest.getBenchmarkHs300Return(),
                backtest.getBenchmarkAllInReturn(),
                backtest.getBenchmarkDcaReturn(),
                backtest.getBenchmarkHs300MaxDrawdown(),
                backtest.getBenchmarkAllInMaxDrawdown(),
                backtest.getBenchmarkDcaMaxDrawdown(),
                backtest.isPassed(),
                backtest.getCreatedDate());
    }
}
