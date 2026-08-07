package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Param;
import feign.RequestLine;

/**
 * 东方财富静态基金估值页客户端。
 *
 * <p>这是参考 AKShare 基金估值页面入口增加的兼容客户端，按页读取
 * {@code fundguzhi{page}.html}，由 {@link EastmoneyFundEstimatePageParser} 解析。
 * AKShare 1.18.12 的 {@code fund_value_estimation_em} 本身仍调用旧 JSON API；该 API
 * 当前返回空 {@code Data}，因此不直接复刻为 Java 源。
 */
public interface EastmoneyFundEstimatePageClient {

    @RequestLine("GET /fundguzhi{page}.html")
    String fetchPageRaw(@Param("page") int page);
}
