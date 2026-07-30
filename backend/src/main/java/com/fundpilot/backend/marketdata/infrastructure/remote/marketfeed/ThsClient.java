package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Param;
import feign.RequestLine;

/** 同花顺基金净值 raw client。 */
public interface ThsClient {

    /**
     * 最原始 GET 请求,返回 Raw String(JSON/JS 字面量),由解析器处理。
     */
    @RequestLine("GET /{fundCode}/json/jsondwjz.json")
    String fetchUnitNavRaw(@Param("fundCode") String fundCode);

    @RequestLine("GET /{fundCode}/json/jsonljjz.json")
    String fetchAccumulatedNavRaw(@Param("fundCode") String fundCode);
}
