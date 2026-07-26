package com.fundpilot.backend.market.client;

import java.util.List;

/**
 * 行情数据源接口(issue #7):统一抽象东方财富/同花顺等外部数据源,支持降级链。
 * <p>各数据源实现本接口,{@code MarketDataSourceChain} 按顺序尝试,全失败抛
 * {@code BusinessException(MARKET_DATA_ALL_SOURCES_FAILED)},不允许 fallback 零值。
 *
 * @see MarketDataSourceChain
 */
@Deprecated(forRemoval = false)
public interface MarketDataSource extends FundNavHistorySource, FundCatalogSource, IndexKlineSource {

    /** 基金净值历史(用于算回撤/年线/60 日新高)。 */
    @Override
    List<FundNavSnapshot> fetchNavHistory(String fundCode);

    /** 全量基金字典(用于回填 fundSubType/benchmarkIndexCode)。 */
    @Override
    List<FundDictEntry> fetchFundDict();

    /** 指数日 K(用于算沪深 300 基准收益/回撤 + 跟踪指数量能)。 */
    @Override
    IndexKline fetchIndexKline(String indexCode, String range);

    /**
     * 指数指定周期 K 线(行情工作台 K 线图切换日/周/月用)。
     *
     * @param indexCode secid 格式(如 "1.000300")
     * @param klt       K 线周期:101=日、102=周、103=月
     * @param lmt       K 线根数上限(如 "400")
     */
    @Override
    default IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        // 默认实现:不支持周期的数据源降级为日 K(range 占位 "6")
        return fetchIndexKline(indexCode, "6");
    }
}
