package com.fundpilot.backend.market.client;

import feign.RequestLine;

/**
 * 新浪财经交易日历 Feign 客户端(task 07-09)。
 * <p>拉 {@code https://finance.sina.com.cn/realstock/company/klc_td_sh.txt},
 * 返回 {@code var datelist="LC/AAA..."} 的 KLC 自定义编码文本,
 * 由 {@link SinaTradingCalendarParser} 用 GraalVM JS 跑 {@code hk_js_decode} 解码为交易日列表。
 * <p>新浪交易日历仅含交易日(不含非交易日),覆盖 1990-12-19 ~ 当年底,调休补班周末正确判为非交易日。
 * 请求头/限流复用 {@link EastmoneyClientConfig} 的共享拦截器与令牌桶。
 */
public interface SinaTradingCalendarClient {

    /** 拉新浪交易日历原始编码文本,由调用方交 {@link SinaTradingCalendarParser#parse} 解码。 */
    @RequestLine("GET /realstock/company/klc_td_sh.txt")
    String fetchTradingCalendarRaw();
}
