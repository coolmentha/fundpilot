package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 定投移动止盈回测结果(issue #57)。
 *
 * @param strategyReturn 策略收益率 = (落袋现金 + 期末市值 − 总投入) / 总投入
 * @param dailyValues    逐日总市值序列(落袋现金 + 持仓市值),与净值序列等长;用于算最大回撤
 * @param trades         回测期间产生的交易记录(INVEST 定投买入 / SELL 止盈卖出)
 */
public record DcaTakeProfitResult(BigDecimal strategyReturn, List<BigDecimal> dailyValues, List<SimTrade> trades) {

    /** 回测模拟交易:定投买入(INVEST)/止盈卖出(SELL)。 */
    public record SimTrade(Instant date, String source, BigDecimal amount, BigDecimal shares, BigDecimal nav) {
    }
}
