package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

import java.util.List;

/**
 * 同花顺(10jqka) Feign 客户端接口,实现 {@link MarketDataSource} 作为东方财富的降级数据源。
 * <p>三条数据线对齐 {@link MarketDataSource}:
 * <ol>
 *   <li>基金净值历史 — 同花顺基金净值接口</li>
 *   <li>基金字典 — 同花顺基金列表接口</li>
 *   <li>指数 K 线 — 同花顺指数行情接口</li>
 * </ol>
 * <p>Feign {@code url} 通过 Spring 属性 {@code ths.base-url} 配置(默认指向同花顺服务)。
 * 解析逻辑由 {@link ThsJsParser} 处理(同花顺响应格式与东方财富不同,需独立解析器)。
 *
 * @see MarketDataSource
 * @see MarketDataSourceChain
 */
public interface ThsClient extends MarketDataSource {

    /**
     * 最原始 GET 请求,返回 Raw String(JSON/JS 字面量),由解析器处理。
     */
    @RequestLine("GET /{path}")
    String fetchRaw(@Param("path") String path);

    /**
     * 获取基金净值历史:调同花顺净值接口并返回结构化快照列表。
     * <p>TODO 同花顺接口字段待对接:同花顺基金净值 API 路径与响应格式需根据实际文档实现解析。
     */
    @Override
    default List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        String raw = fetchRaw("fund/netvalue/" + fundCode);
        return ThsJsParser.parseNavHistory(raw);
    }

    /**
     * 获取全量基金字典:调同花顺基金列表接口并返回条目列表。
     * <p>TODO 同花顺接口字段待对接:同花顺基金字典 API 路径与响应格式需根据实际文档实现解析。
     */
    @Override
    default List<FundDictEntry> fetchFundDict() {
        String raw = fetchRaw("fund/list");
        return ThsJsParser.parseFundDict(raw);
    }

    /**
     * 获取指数 K 线:调同花顺指数行情接口并返回 OHLCV。
     * <p>TODO 同花顺接口字段待对接:同花顺指数 K 线 API 路径与响应格式需根据实际文档实现解析。
     */
    @Override
    default IndexKline fetchIndexKline(String indexCode, String range) {
        String raw = fetchRaw("index/kline?code=" + indexCode + "&range=" + range);
        return ThsJsParser.parseIndexKline(raw);
    }
}
