package com.fundpilot.backend.fund.client;

import feign.Param;
import feign.RequestLine;

/**
 * 东方财富基金费率 Feign 客户端(fundf10.eastmoney.com 域名)。
 * <p>{@code jjfl_<code>.html} 是服务端渲染 HTML,含申购费率(原|优惠)、赎回费率阶梯、销售服务费。
 * 由 {@link FundFeeHtmlParser} 解析。共享 {@code EastmoneyClientConfig} 的限流桶(2 req/s)。
 */
public interface EastmoneyFundFeeClient {

    /**
     * 最原始 GET 请求,返回 jjfl 页 HTML 文本,由解析器处理。
     *
     * @param fundCode 基金代码(如 001071)
     * @return HTML 文本
     */
    @RequestLine("GET /jjfl_{code}.html")
    String fetchFeeHtml(@Param("code") String fundCode);
}
