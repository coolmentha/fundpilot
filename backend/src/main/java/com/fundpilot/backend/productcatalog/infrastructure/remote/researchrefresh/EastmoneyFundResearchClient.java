package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import feign.Param;
import feign.RequestLine;

public interface EastmoneyFundResearchClient {
    @RequestLine("GET /jbgk_{code}.html")
    String fetchProfile(@Param("code") String fundCode);

    @RequestLine("GET /FundArchivesDatas.aspx?type=gmbd&mode=0&code={code}")
    String fetchScale(@Param("code") String fundCode);

    @RequestLine("GET /FundArchivesDatas.aspx?type=jjcc&code={code}&topline=10&year={year}&month=12")
    String fetchHoldings(@Param("code") String fundCode, @Param("year") int year);
}
