package com.fundpilot.backend.alerting.domain.condition;

/** 指标取值的来源：行情侧按需计算，或提醒侧已取得的基金事实。 */
public enum IndicatorSource {

    /** 行情指标：由 MarketData 按需计算（净值历史 / 指数 K 线 / 指数估值）。 */
    MARKET_DATA,
    /** 基金事实：由提醒评估当日的关注基金快照直接提供（当日涨跌幅、持仓收益率）。 */
    FUND_FACT
}