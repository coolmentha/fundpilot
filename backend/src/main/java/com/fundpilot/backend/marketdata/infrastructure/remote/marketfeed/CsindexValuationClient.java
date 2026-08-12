package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Param;
import feign.RequestLine;

/** 中证专用指数 PE 历史接口，单独保留以免破坏既有 K 线客户端的函数式测试契约。 */
public interface CsindexValuationClient {
    @RequestLine("GET /csindex-home/perf/indexCsiDsPe?indexCode={indexCode}&startDate={startDate}&endDate={endDate}")
    String fetchIndexCsiDsPe(@Param("indexCode") String indexCode,
                             @Param("startDate") String startDate,
                             @Param("endDate") String endDate);
}
