package com.fundpilot.backend.market.client;

import java.util.List;

/**
 * 同花顺响应解析器(占位)。
 * <p>同花顺(10jqka)接口响应格式与东方财富不同,需独立解析器。
 * 本期为架构占位,实际解析逻辑待同花顺 API 文档对接后实现;
 * 调用时抛 {@link UnsupportedOperationException} 以确保降级链正确跳过同花顺数据源。
 *
 * @see ThsClient
 */
public final class ThsJsParser {

    private ThsJsParser() {
    }

    /**
     * 解析同花顺基金净值历史响应。
     * TODO 待同花顺 API 文档对接后实现。
     */
    public static List<FundNavSnapshot> parseNavHistory(String raw) {
        throw new UnsupportedOperationException("同花顺净值历史解析待实现");
    }

    /**
     * 解析同花顺基金字典响应。
     * TODO 待同花顺 API 文档对接后实现。
     */
    public static List<FundDictEntry> parseFundDict(String raw) {
        throw new UnsupportedOperationException("同花顺基金字典解析待实现");
    }

    /**
     * 解析同花顺指数 K 线响应。
     * TODO 待同花顺 API 文档对接后实现。
     */
    public static IndexKline parseIndexKline(String raw) {
        throw new UnsupportedOperationException("同花顺指数 K 线解析待实现");
    }
}
