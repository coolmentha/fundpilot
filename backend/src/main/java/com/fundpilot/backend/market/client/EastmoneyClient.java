package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

import java.util.List;

/**
 * 东方财富 Feign 客户端接口。三条数据线:
 * <ol>
 *   <li>基金净值历史 — pingzhongdata.js</li>
 *   <li>基金字典 — fundcode_search.js</li>
 *   <li>指数 K 线 — push2his.eastmoney.com</li>
 * </ol>
 * 所有请求经 {@link EastmoneyClientConfig#requestInterceptor()} 加 Referer/UA 头,
 * 经 {@link EastmoneyClientConfig#semaphore()} 限流(共享 Semaphore,约每秒 2-3 次)。
 * <p>Feign {@code url} 通过 Spring 属性 {@code eastmoney.base-url} 配置(默认指向东方财富服务),
 * 测试时通过 {@code feign.Feign.builder()} 编程指向 MockWebServer 地址。
 *
 * @see EastmoneyClientConfig
 * @see MarketDataSource
 */
public interface EastmoneyClient extends MarketDataSource {

    /**
     * 最原始 GET 请求,返回 Raw String(JS 字面量),由解析器处理。
     */
    @RequestLine("GET /{path}")
    String fetchRaw(@Param("path") String path);

    /**
     * 获取基金净值历史:调 pingzhongdata.js 并返回结构化快照列表。
     */
    default List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        String raw = fetchRaw("pingzhongdata/" + fundCode + ".js");
        return EastmoneyJsParser.parseNavHistory(raw);
    }

    /**
     * 获取全量基金字典:调 fundcode_search.js 并返回条目列表。
     */
    default List<FundDictEntry> fetchFundDict() {
        String raw = fetchRaw("js/fundcode_search.js");
        return EastmoneyJsParser.parseFundDict(raw);
    }

    /**
     * 获取指数 K 线:调 push2his.eastmoney.com 并返回 OHLCV。
     */
    default IndexKline fetchIndexKline(String indexCode, String range) {
        String raw = fetchRaw("api/v2/...?code=" + indexCode + "&range=" + range);
        return EastmoneyJsParser.parseIndexKline(raw);
    }
}