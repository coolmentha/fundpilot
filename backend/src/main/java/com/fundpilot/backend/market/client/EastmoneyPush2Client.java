package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

/**
 * 东方财富 push2.eastmoney.com 实时行情 Feign 客户端(独立接口,实时行情接口在 push2 域名,
 * 与历史 K 线的 push2his.eastmoney.com 不同,故单独配置 target)。
 *
 * <p>三条数据线:
 * <ul>
 *   <li>{@link #fetchIndexRealtimeRaw(String)} 批量指数实时行情(ulist.np/get)</li>
 *   <li>{@link #fetchSectorListRaw(String)} 行业板块涨跌 + 资金流向(clist/get,fs=m:90 t:2)</li>
 *   <li>{@link #fetchNorthboundRaw()} 北向资金实时净流入(kamt.rtmin/get)</li>
 * </ul>
 * 请求头/限流复用 {@link EastmoneyClientConfig} 的共享拦截器与令牌桶。
 * fields 中的逗号用 {@code %2C} 编码(同 {@link EastmoneyKlineClient},Feign URI template 会截断字面逗号)。
 */
public interface EastmoneyPush2Client {

    /**
     * 批量指数实时行情。secids 用逗号分隔(此处不编码,东方财富按字面逗号解析)。
     * <p>fields:f2 最新价、f3 涨跌幅、f4 涨跌额、f6 成交额、f12 代码、f14 名称、
     * f104 上涨家数、f105 下跌家数。
     *
     * @param secids secid 列表(逗号分隔,如 "1.000001,1.000300,0.399006")
     */
    @RequestLine("GET /api/qt/ulist.np/get?fields=f2%2Cf3%2Cf4%2Cf6%2Cf12%2Cf14%2Cf104%2Cf105&secids={secids}")
    String fetchIndexRealtimeRaw(@Param("secids") String secids);

    /**
     * 行业板块涨跌 + 资金流向。fs=m:90 t:2 是行业板块过滤条件。
     * <p>fields:f3 涨跌幅、f6 成交额、f12 板块代码、f14 板块名称、f62 主力净流入。
     *
     * @param sort 排序字段(如 "f3" 按涨跌幅、"f6" 按成交额)
     */
    @RequestLine("GET /api/qt/clist/get?pn=1&pz=20&po=1&np=1&fields=f3%2Cf6%2Cf12%2Cf14%2Cf62&fs=m:90+t:2&fid={sort}")
    String fetchSectorListRaw(@Param("sort") String sort);

    /**
     * 北向资金实时净流入(沪深股通合计)。s2n 数组每分钟一条 CSV。
     */
    @RequestLine("GET /api/qt/kamt.rtmin/get?fields1=f1%2Cf2%2Cf3%2Cf4&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56")
    String fetchNorthboundRaw();
}
