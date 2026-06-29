package com.fundpilot.backend.market.client;

import java.util.List;

/**
 * 行情数据源接口(issue #7):统一抽象东方财富/同花顺等外部数据源,支持降级链。
 * <p>各数据源实现本接口,{@code MarketDataSourceChain} 按顺序尝试,全失败抛
 * {@code BusinessException(MARKET_DATA_ALL_SOURCES_FAILED)},不允许 fallback 零值。
 *
 * @see MarketDataSourceChain
 */
public interface MarketDataSource {

    /** 基金净值历史(用于算回撤/年线/60 日新高)。 */
    List<FundNavSnapshot> fetchNavHistory(String fundCode);

    /** 全量基金字典(用于回填 fundSubType/benchmarkIndexCode)。 */
    List<FundDictEntry> fetchFundDict();

    /** 指数日 K(用于算沪深 300 基准收益/回撤 + 跟踪指数量能)。 */
    IndexKline fetchIndexKline(String indexCode, String range);
}
