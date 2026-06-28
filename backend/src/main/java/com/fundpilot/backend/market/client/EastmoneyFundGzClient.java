package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

/**
 * 东方财富基金盘中估值 Feign 客户端(fundgz.1234567.com.cn 域名,issue #36)。
 * <p>返回 JSONP 包裹 {@code jsonpgz({...});},由 {@link EastmoneyJsParser#parseFundGz} 解析。
 * 与基金净值/字典(fund.eastmoney.com)、指数 K 线(push2his.eastmoney.com)是不同域名,故独立 client。
 * <p>请求头/限流复用 {@link EastmoneyClientConfig} 的共享拦截器与 RateLimiter。
 */
public interface EastmoneyFundGzClient {

    /**
     * 拉基金盘中估值原始 JSONP 响应。
     *
     * @param fundCode 基金代码(如 008585)
     * @return {@code jsonpgz({...});} 原始文本
     */
    @RequestLine("GET /js/{code}.js")
    String fetchGzRaw(@Param("code") String fundCode);
}
