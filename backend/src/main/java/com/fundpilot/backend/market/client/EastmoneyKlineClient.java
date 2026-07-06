package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

/**
 * 东方财富指数 K 线 Feign 客户端(独立接口,K 线接口在 push2his.eastmoney.com 域名,
 * 与基金净值/字典的 fund.eastmoney.com 不同,故单独配置 target)。
 * <p>请求头/限流复用 {@link EastmoneyClientConfig} 的共享拦截器与 Semaphore。
 */
public interface EastmoneyKlineClient {

    /**
     * 拉 push2his 指数 K 线原始 JSON 响应,由 {@link EastmoneyJsParser#parseIndexKline} 解析。
     * <p>fields1/fields2 的逗号用 {@code %2C} 编码:Feign URI template 会把字面逗号截断,
     * 导致东方财富只返回首个 field(f51),K 线缺 OHLCV 全被解析器跳过。编码后东方财富正常解码返回完整字段。
     *
     * @param secid secid 格式(如 "1.000300")
     * @param lmt   返回的 K 线根数上限(如 "400")
     */
    @RequestLine("GET /api/qt/stock/kline/get?secid={secid}&fields1=f1%2Cf2%2Cf3%2Cf4%2Cf5%2Cf6"
            + "&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56%2Cf57%2Cf58&klt=101&fqt=1&beg=0&end=20500101&lmt={lmt}")
    String fetchKlineRaw(@Param("secid") String secid, @Param("lmt") String lmt);

    /**
     * 拉指定周期(日/周/月)的指数 K 线,供行情工作台 K 线图切换周期用。
     *
     * @param secid secid 格式
     * @param klt   K 线周期:101=日K、102=周K、103=月K
     * @param lmt   K 线根数上限
     */
    @RequestLine("GET /api/qt/stock/kline/get?secid={secid}&fields1=f1%2Cf2%2Cf3%2Cf4%2Cf5%2Cf6"
            + "&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56%2Cf57%2Cf58&klt={klt}&fqt=1&beg=0&end=20500101&lmt={lmt}")
    String fetchKlineRaw(@Param("secid") String secid, @Param("klt") String klt, @Param("lmt") String lmt);
}
