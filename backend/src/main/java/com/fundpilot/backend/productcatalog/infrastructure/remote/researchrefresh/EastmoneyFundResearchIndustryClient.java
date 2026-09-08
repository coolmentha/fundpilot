package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import feign.Param;
import feign.RequestLine;

public interface EastmoneyFundResearchIndustryClient {
    @RequestLine("GET /f10/HYPZ/?fundCode={code}&year={year}")
    String fetchIndustry(@Param("code") String fundCode, @Param("year") int year);
}
