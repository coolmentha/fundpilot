package com.fundpilot.backend.market.client;

import feign.RequestLine;

public interface ThsFundInfoClient {

    @RequestLine("GET /data/Net/info/all_code_asc_0_0_1_40000_0_0_0_jsonp_g.html")
    String fetchFundDictRaw();
}
