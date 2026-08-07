package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Param;
import feign.RequestLine;

/**
 * 腾讯证券日线客户端。
 *
 * <p>请求方式与 AKShare {@code stock_zh_index_daily_tx} 当前实现一致：
 * {@code proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get}，
 * 使用 {@code symbol,day,start,end,640,qfq} 参数，响应是
 * {@code kline_dayqfq={...}} JSON 赋值文本。该接口同时被 AKShare 的
 * {@code stock_zh_index_daily_tx} 和 {@code stock_zh_a_hist_tx} 使用；本项目
 * 当前只把指数日线接入 {@link TencentIndexMarketDataSource}。
 */
public interface TencentIndexClient {

    @RequestLine("GET /ifzqgtimg/appstock/app/newfqkline/get?_var=kline_dayqfq"
            + "&param={symbol}%2Cday%2C{startDate}%2C{endDate}%2C640%2Cqfq"
            + "&r=0.8205512681390605")
    String fetchKlineRaw(@Param("symbol") String symbol,
                         @Param("startDate") String startDate,
                         @Param("endDate") String endDate);
}
