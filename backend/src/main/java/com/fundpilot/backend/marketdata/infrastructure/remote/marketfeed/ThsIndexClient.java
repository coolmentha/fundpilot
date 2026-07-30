package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Param;
import feign.RequestLine;

public interface ThsIndexClient {

    @RequestLine("GET /v6/line/{internalCode}/01/last.js")
    String fetchDailyKlineRaw(@Param("internalCode") String internalCode);
}
