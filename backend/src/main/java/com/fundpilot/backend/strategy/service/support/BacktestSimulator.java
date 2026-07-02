package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 回测策略模拟器(ADR-0015 重写):委托 {@link DcaTakeProfitSimulator}。
 * <p>旧择时金字塔实现已废弃;保留本类名供 {@code DefaultStrategyBacktestService} 调用,
 * 内部改为定投移动止盈模拟。与生产信号引擎共用 {@link TrailingStopEngine}——测回测等于测策略本体。
 */
public final class BacktestSimulator {

    private BacktestSimulator() {
    }

    /**
     * 定投移动止盈回测。
     *
     * @param navSequence 累计净值序列(升序)
     * @param navDates    对应日期(等长)
     * @param indicators  逐日行情指标(ADR-0015 后移动止盈不再需要年线/MACD/量能;保留参数以维持旧调用契约,内部忽略)
     * @param params      定投移动止盈回测参数(dcaAmount + TakeProfitParams)
     * @return 回测结果(策略收益率 + 逐日市值 + 交易记录)
     */
    public static DcaTakeProfitResult simulate(List<BigDecimal> navSequence, List<Instant> navDates,
                                               List<MarketIndicators> indicators, BacktestParams params) {
        return DcaTakeProfitSimulator.simulate(navSequence, navDates, params.dcaAmount(),
                params.takeProfitParams(), params.fundCategory());
    }
}