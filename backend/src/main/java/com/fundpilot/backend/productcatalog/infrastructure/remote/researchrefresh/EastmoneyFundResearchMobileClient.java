package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import feign.Param;
import feign.RequestLine;

public interface EastmoneyFundResearchMobileClient {
    @RequestLine("GET /FundMNewApi/FundMNInverstPosition?FCODE={code}&deviceid=fundpilot&plat=Android&product=EFund&version=6.3.8")
    String fetchPosition(@Param("code") String fundCode);

    @RequestLine("GET /FundMNewApi/FundMNAssetAllocation?FCODE={code}&deviceid=fundpilot&plat=Android&product=EFund&version=6.3.8")
    String fetchAllocation(@Param("code") String fundCode);

    /**
     * 基金详情信息:返回 FTYPE/INDEXCODE/INDEXNAME/BENCH 等字段,
     * 其中 INDEXCODE 是指数型基金真实跟踪的指数代码(裸 6 位,如 "980017"),
     * 主动/非指数基金为占位符 "--"。用于创建基金时准确识别跟踪标的。
     */
    @RequestLine("GET /FundMNewApi/FundMNDetailInformation?FCODE={code}&deviceid=fundpilot&plat=Android&product=EFund&version=6.3.8")
    String fetchDetailInformation(@Param("code") String fundCode);
}
