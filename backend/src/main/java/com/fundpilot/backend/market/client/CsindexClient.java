package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

/**
 * 中证指数公司(csindex.com.cn)Feign 客户端:拉取指数日线历史。
 * <p>借鉴 akshare {@code stock_zh_index_hist_csindex}——中证指数公司是 CSI 主题指数(930xxx/931xxx)
 * 的官方发布方,其 {@code /csindex-home/perf/index-perf} 接口公开返回 OHLCV 日线 JSON,且不封 IP、
 * 不要求 Referer/Cookie。东方财富 push2his 在 VPS 上被 IP 限流(http 000),故引入本源作为指数 K 线主源。
 * <p>覆盖范围:CSI 主题指数(930713 等)+ 中证编制的沪市指数(000300 沪深 300、000016 上证 50、
 * 000852 中证 1000)。深交所指数(399xxx)不在中证公司编制范围,本接口返空 data[]——由
 * {@link CsindexMarketDataSource} 抛异常使降级链回退东方财富。
 * <p>仅提供日线;周/月 K 由 {@link CsindexMarketDataSource} 在日线上聚合。
 *
 * @param indexCode 裸指数代码(无市场后缀,如 "930713"/"000300")
 * @param startDate 起始日 yyyyMMdd(如 "20200101")
 * @param endDate   结束日 yyyyMMdd
 */
public interface CsindexClient {

    @RequestLine("GET /csindex-home/perf/index-perf?indexCode={indexCode}&startDate={startDate}&endDate={endDate}")
    String fetchIndexPerf(@Param("indexCode") String indexCode,
                          @Param("startDate") String startDate,
                          @Param("endDate") String endDate);
}
