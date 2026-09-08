package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import feign.Param;
import feign.RequestLine;

public interface EastmoneyFundResearchMobileClient {
    @RequestLine("GET /FundMNewApi/FundMNInverstPosition?FCODE={code}&deviceid=fundpilot&plat=Android&product=EFund&version=6.3.8")
    String fetchPosition(@Param("code") String fundCode);

    @RequestLine("GET /FundMNewApi/FundMNAssetAllocation?FCODE={code}&deviceid=fundpilot&plat=Android&product=EFund&version=6.3.8")
    String fetchAllocation(@Param("code") String fundCode);
}
