package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.RequestLine;

/** 对应 AKShare {@code fund_etf_spot_ths} 的同花顺 ETF 最近确认净值列表客户端。 */
public interface ThsEtfSpotClient {

    @RequestLine("GET /data/Net/info/ETF_rate_desc_0_0_1_9999_0_0_0_jsonp_g.html")
    String fetchSpotRaw();
}
